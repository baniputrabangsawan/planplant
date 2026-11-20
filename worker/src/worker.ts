export interface D1PreparedStatement {
  bind(...values: unknown[]): D1PreparedStatement;
}

export interface D1Result {
  success: boolean;
  meta: { changes: number };
}

export interface D1Database {
  prepare(sql: string): D1PreparedStatement;
  batch(statements: D1PreparedStatement[]): Promise<D1Result[]>;
}

export interface Env {
  PLANTNET_API_KEY?: string;
  FAMILY_TOKEN?: string;
  DB?: D1Database;
}

interface Candidate {
  scientificName: string;
  commonName: string | null;
  score: number;
}

interface HealthCandidate {
  code: string;
  label: string;
  score: number;
}

/** Attribution is required whenever a photo is shown, so unverifiable licences are dropped. */
interface NearbyPhoto {
  thumbUrl: string;
  url: string;
  license: string;
  credit: string;
}

/** Counts are public occurrence records, not plant abundance or native-range evidence. */
interface NearbySpecies {
  scientificName: string;
  commonName: string | null;
  description: string | null;
  wikiUrl: string | null;
  observations: number;
  latitude: number;
  longitude: number;
  photo: NearbyPhoto | null;
}

// Cloudflare exposes a native constant-time comparison on SubtleCrypto.
interface WorkerSubtleCrypto extends SubtleCrypto {
  timingSafeEqual(a: ArrayBuffer, b: ArrayBuffer): boolean;
}

class RequestError extends Error {
  code: string;
  status: number;

  constructor(code: string, status: number) {
    super(code);
    this.code = code;
    this.status = status;
  }
}

const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
// Pl@ntNet accepts up to five images of the same plant individual.
const MAX_HEALTH_IMAGES = 5;
const MAX_MULTIPART_OVERHEAD = 16 * 1024;
const UPLOAD_DEADLINE_MS = 15_000;
const PROVIDER_DEADLINE_MS = 20_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const encoder = new TextEncoder();
const RESERVE_QUOTA = `INSERT INTO identification_requests (request_id, day, device)
SELECT ?, ?, ?
WHERE (SELECT COUNT(*) FROM identification_requests WHERE day = ?) < 100
AND (SELECT COUNT(*) FROM identification_requests WHERE day = ? AND device = ?) < 20`;

function json(body: unknown, status = 200, extraHeaders?: Record<string, string>): Response {
  return Response.json(body, {
    status,
    headers: {
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...extraHeaders,
    },
  });
}

function errorResponse(code: string, status: number): Response {
  return json({ error: { code } }, status);
}

function configured(env: Env): env is Required<Env> {
  return Boolean(env.PLANTNET_API_KEY?.trim() && env.FAMILY_TOKEN?.trim() && env.DB);
}

async function digest(value: string): Promise<ArrayBuffer> {
  return crypto.subtle.digest("SHA-256", encoder.encode(value));
}

async function authorized(request: Request, token: string): Promise<boolean> {
  const authorization = request.headers.get("Authorization") ?? "";
  const match = /^Bearer ([^\s]+)$/i.exec(authorization);
  // Both inputs become fixed-size digests before native constant-time comparison.
  const [provided, expected] = await Promise.all([digest(match?.[1] ?? ""), digest(token)]);
  const equal = (crypto.subtle as WorkerSubtleCrypto).timingSafeEqual(provided, expected);
  return Boolean(match) && equal;
}

async function deadline<T>(milliseconds: number, operation: (signal: AbortSignal) => Promise<T>): Promise<T> {
  const controller = new AbortController();
  const expired = Promise.withResolvers<never>();
  const timer = setTimeout(() => {
    expired.reject(new RequestError("TIMEOUT", 504));
    controller.abort();
  }, milliseconds);
  try {
    return await Promise.race([operation(controller.signal), expired.promise]);
  } finally {
    clearTimeout(timer);
  }
}

function cancelBody(body: ReadableStream<Uint8Array> | null): void {
  if (body) void body.cancel().catch(() => {});
}

async function readImage(request: Request): Promise<Uint8Array<ArrayBuffer>> {
  const length = request.headers.get("Content-Length");
  if (length !== null) {
    if (!/^\d+$/.test(length)) throw new RequestError("INVALID_IMAGE", 400);
    if (Number(length) > MAX_IMAGE_BYTES) {
      cancelBody(request.body);
      throw new RequestError("IMAGE_TOO_LARGE", 413);
    }
  }
  if (!request.body) throw new RequestError("INVALID_IMAGE", 400);
  const reader = request.body.getReader();
  let complete = false;
  try {
    return await deadline(UPLOAD_DEADLINE_MS, async signal => {
      const cancel = () => { void reader.cancel().catch(() => {}); };
      signal.addEventListener("abort", cancel, { once: true });
      try {
        // One fixed-size buffer also bounds overhead from arbitrarily small chunks.
        const bytes = new Uint8Array(MAX_IMAGE_BYTES);
        let size = 0;
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          if (value.byteLength > MAX_IMAGE_BYTES - size) throw new RequestError("IMAGE_TOO_LARGE", 413);
          bytes.set(value, size);
          size += value.byteLength;
        }
        // Require SOI, a JPEG marker, and EOI; reject truncated/non-JPEG uploads before quota.
        if (size < 5 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff ||
            bytes[size - 2] !== 0xff || bytes[size - 1] !== 0xd9) {
          throw new RequestError("INVALID_IMAGE", 400);
        }
        complete = true;
        return bytes.subarray(0, size);
      } finally {
        signal.removeEventListener("abort", cancel);
      }
    });
  } catch (error) {
    if (error instanceof RequestError) throw error;
    throw new RequestError("INVALID_IMAGE", 400);
  } finally {
    if (!complete) void reader.cancel().catch(() => {});
    if (complete) reader.releaseLock();
  }
}

function validateMultipartFraming(bytes: Uint8Array, boundary: string, maxImages: number): void {
  // Bound part count and header work before the platform creates FormData objects.
  const first = encoder.encode(`--${boundary}\r\n`);
  const delimiter = encoder.encode(`\r\n--${boundary}`);
  if (!first.every((byte, index) => bytes[index] === byte)) throw new RequestError("INVALID_IMAGE", 400);
  let parts = 1;
  let headerStart = first.length;
  let overhead = first.length;
  for (;;) {
    let headerEnd = headerStart;
    while (headerEnd + 3 < bytes.length &&
        !(bytes[headerEnd] === 13 && bytes[headerEnd + 1] === 10 &&
          bytes[headerEnd + 2] === 13 && bytes[headerEnd + 3] === 10)) {
      if (headerEnd - headerStart + overhead > MAX_MULTIPART_OVERHEAD) throw new RequestError("IMAGE_TOO_LARGE", 413);
      headerEnd++;
    }
    if (headerEnd + 3 >= bytes.length) throw new RequestError("INVALID_IMAGE", 400);
    overhead += headerEnd + 4 - headerStart;
    let next = bytes.indexOf(13, headerEnd + 4);
    while (next !== -1) {
      const after = next + delimiter.length;
      if (delimiter.every((byte, index) => bytes[next + index] === byte) &&
          ((bytes[after] === 45 && bytes[after + 1] === 45) ||
            (bytes[after] === 13 && bytes[after + 1] === 10))) break;
      next = bytes.indexOf(13, next + 1);
    }
    if (next === -1) throw new RequestError("INVALID_IMAGE", 400);
    if (next - (headerEnd + 4) > MAX_IMAGE_BYTES) throw new RequestError("IMAGE_TOO_LARGE", 413);
    const after = next + delimiter.length;
    overhead += delimiter.length + 2;
    if (overhead > MAX_MULTIPART_OVERHEAD) throw new RequestError("IMAGE_TOO_LARGE", 413);
    if (bytes[after] === 45) {
      const end = after + 2;
      if (end !== bytes.length &&
          !(end + 2 === bytes.length && bytes[end] === 13 && bytes[end + 1] === 10)) {
        throw new RequestError("INVALID_IMAGE", 400);
      }
      return;
    }
    if (++parts > maxImages) throw new RequestError("INVALID_IMAGE", 400);
    headerStart = after + 2;
  }
}

async function readMultipartImages(request: Request, maxImages: number): Promise<Blob[]> {
  const maxUploadBytes = maxImages * MAX_IMAGE_BYTES + MAX_MULTIPART_OVERHEAD;
  // A short boundary and a bounded body must be established before formData allocates.
  const contentType = request.headers.get("Content-Type") ?? "";
  const multipart = /^multipart\/form-data\s*;\s*boundary=(?:"([0-9a-z'()+_,./:=?-]{1,70})"|([0-9a-z'()+_,./:=?-]{1,70}))\s*$/i.exec(contentType);
  if (!multipart) throw new RequestError("INVALID_IMAGE", 400);
  const boundary = multipart[1] ?? multipart[2];
  const length = request.headers.get("Content-Length");
  if (length !== null) {
    if (!/^\d+$/.test(length)) throw new RequestError("INVALID_IMAGE", 400);
    if (Number(length) > maxUploadBytes) {
      cancelBody(request.body);
      throw new RequestError("IMAGE_TOO_LARGE", 413);
    }
  }
  if (!request.body) throw new RequestError("INVALID_IMAGE", 400);
  const reader = request.body.getReader();
  let complete = false;
  try {
    return await deadline(UPLOAD_DEADLINE_MS, async signal => {
      const cancel = () => { void reader.cancel().catch(() => {}); };
      signal.addEventListener("abort", cancel, { once: true });
      try {
        // Fixed storage bounds both bytes and bookkeeping for tiny streamed chunks.
        const bytes = new Uint8Array(maxUploadBytes);
        let size = 0;
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          if (value.byteLength > maxUploadBytes - size) throw new RequestError("IMAGE_TOO_LARGE", 413);
          bytes.set(value, size);
          size += value.byteLength;
        }
        complete = true;
        validateMultipartFraming(bytes.subarray(0, size), boundary, maxImages);
        const form = await new Response(bytes.subarray(0, size), {
          headers: { "Content-Type": contentType },
        }).formData();
        const images: Blob[] = [];
        let imageBytes = 0;
        for (const [name, value] of form.entries()) {
          if (name !== "images" || typeof value === "string" || value.type !== "image/jpeg" ||
              images.length >= maxImages || value.size < 5) {
            throw new RequestError("INVALID_IMAGE", 400);
          }
          if (value.size > MAX_IMAGE_BYTES) throw new RequestError("IMAGE_TOO_LARGE", 413);
          const start = new Uint8Array(await value.slice(0, 3).arrayBuffer());
          const finish = new Uint8Array(await value.slice(-2).arrayBuffer());
          if (start[0] !== 0xff || start[1] !== 0xd8 || start[2] !== 0xff || finish[0] !== 0xff || finish[1] !== 0xd9) {
            throw new RequestError("INVALID_IMAGE", 400);
          }
          images.push(value);
          imageBytes += value.size;
        }
        if (images.length === 0) throw new RequestError("INVALID_IMAGE", 400);
        if (size - imageBytes > MAX_MULTIPART_OVERHEAD) throw new RequestError("IMAGE_TOO_LARGE", 413);
        return images;
      } finally {
        signal.removeEventListener("abort", cancel);
      }
    });
  } catch (error) {
    if (error instanceof RequestError) throw error;
    throw new RequestError("INVALID_IMAGE", 400);
  } finally {
    if (!complete) void reader.cancel().catch(() => {});
    if (complete) reader.releaseLock();
  }
}

async function reserveQuota(db: D1Database, device: string): Promise<boolean> {
  const now = Date.now();
  const day = new Date(now).toISOString().slice(0, 10);
  const cutoff = new Date(now - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const hashedDevice = Array.from(new Uint8Array(await digest(device)), byte => byte.toString(16).padStart(2, "0")).join("");
  // D1 executes batches transactionally. The conditional INSERT is itself atomic,
  // so concurrent isolates cannot race a separate quota read and increment.
  const results = await db.batch([
    db.prepare("DELETE FROM identification_requests WHERE day < ?").bind(cutoff),
    db.prepare(RESERVE_QUOTA).bind(crypto.randomUUID(), day, hashedDevice, day, day, hashedDevice),
  ]);
  if (results.length !== 2 || results.some(result => !result.success) ||
      (results[1].meta.changes !== 0 && results[1].meta.changes !== 1)) {
    throw new RequestError("PROVIDER_UNAVAILABLE", 503);
  }
  // Never refund a reservation: failed provider attempts still consume quota.
  return results[1].meta.changes === 1;
}

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function nonemptyName(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const name = value.trim();
  return name && name.length <= 200 && !/[\u0000-\u001f\u007f]/.test(name) ? name : null;
}

function candidatesFrom(body: unknown): Candidate[] {
  if (!record(body) || !Array.isArray(body.results)) {
    throw new RequestError("PROVIDER_UNAVAILABLE", 502);
  }
  const candidates: Candidate[] = [];
  for (const result of body.results) {
    if (!record(result) || !record(result.species)) continue;
    const scientificName = nonemptyName(result.species.scientificNameWithoutAuthor);
    if (!scientificName || typeof result.score !== "number" || !Number.isFinite(result.score) ||
        result.score < 0 || result.score > 1) continue;
    let commonName: string | null = null;
    if (Array.isArray(result.species.commonNames)) {
      for (const value of result.species.commonNames) {
        commonName = nonemptyName(value);
        if (commonName) break;
      }
    }
    candidates.push({ scientificName, commonName, score: result.score });
  }
  candidates.sort((a, b) => b.score - a.score);
  const seen = new Set<string>();
  const unique: Candidate[] = [];
  for (const candidate of candidates) {
    const identity = candidate.scientificName.toLowerCase();
    if (seen.has(identity)) continue;
    seen.add(identity);
    unique.push(candidate);
    if (unique.length === 3) break;
  }
  return unique;
}

function healthCandidatesFrom(body: unknown): HealthCandidate[] {
  if (!record(body) || !Array.isArray(body.results)) {
    throw new RequestError("PROVIDER_UNAVAILABLE", 502);
  }
  const candidates: HealthCandidate[] = [];
  for (const result of body.results) {
    if (!record(result)) continue;
    const code = nonemptyName(result.name);
    const label = nonemptyName(result.description);
    if (!code || !/^[A-Z0-9]{1,20}$/i.test(code) || !label || typeof result.score !== "number" || !Number.isFinite(result.score) ||
        result.score < 0 || result.score > 1) continue;
    candidates.push({ code: code.toUpperCase(), label, score: result.score });
  }
  candidates.sort((a, b) => b.score - a.score);
  const seen = new Set<string>();
  const unique: HealthCandidate[] = [];
  for (const candidate of candidates) {
    if (seen.has(candidate.code)) continue;
    seen.add(candidate.code);
    unique.push(candidate);
    if (unique.length === 3) break;
  }
  return unique;
}

async function providerResults(
  images: Blob[],
  apiKey: string,
  endpoint: "identify/all" | "diseases/identify",
): Promise<unknown> {
  return deadline(PROVIDER_DEADLINE_MS, async signal => {
    const url = new URL(`https://my-api.plantnet.org/v2/${endpoint}`);
    url.searchParams.set("api-key", apiKey);
    url.searchParams.set("lang", "id");
    url.searchParams.set("nb-results", "3");
    url.searchParams.set("include-related-images", "false");
    const form = new FormData();
    for (const [index, image] of images.entries()) {
      form.append("images", image, images.length === 1 ? "plant.jpg" : `plant-${index + 1}.jpg`);
      form.append("organs", "auto");
    }
    // Never retry or follow redirects with the provider credential in the URL.
    const response = await fetch(url, {
      method: "POST",
      body: form,
      signal,
      redirect: "manual",
      headers: { "User-Agent": "JagaDaun/0.1", Accept: "application/json" },
    });
    if (!response.ok) {
      cancelBody(response.body);
      if (response.status === 401 || response.status === 403) throw new RequestError("PROVIDER_AUTH", 503);
      if (response.status === 429) throw new RequestError("PROVIDER_QUOTA", 429);
      if (response.status === 404 || response.status === 422) throw new RequestError("NOT_RECOGNIZED", 422);
      throw new RequestError("PROVIDER_UNAVAILABLE", 502);
    }
    return response.json();
  });
}

async function mappedProviderResults(images: Blob[], apiKey: string, healthCheck: boolean) {
  let body: unknown;
  try {
    body = await providerResults(images, apiKey, healthCheck ? "diseases/identify" : "identify/all");
  } catch (error) {
    // One unusable angle must not discard the whole individual: the provider rejects a batch
    // outright when any single image is unidentifiable. Retry at most the first two photos so a
    // bad set cannot multiply paid provider credits.
    if (!(error instanceof RequestError) || error.code !== "NOT_RECOGNIZED" || images.length < 2) throw error;
    let recovered: unknown = null;
    for (const image of images.slice(0, 2)) {
      try {
        recovered = await providerResults([image], apiKey, healthCheck ? "diseases/identify" : "identify/all");
        break;
      } catch (inner) {
        if (!(inner instanceof RequestError) || inner.code !== "NOT_RECOGNIZED") throw inner;
      }
    }
    if (recovered === null) throw error;
    body = recovered;
  }
  const candidates = healthCheck ? healthCandidatesFrom(body) : candidatesFrom(body);
  if (candidates.length === 0) throw new RequestError("NOT_RECOGNIZED", 422);
  // Disease candidates remain hypotheses at every score; this flag only adds display caution.
  const uncertain = healthCheck
    ? candidates[0].score < 0.5 || (candidates.length > 1 && candidates[0].score - candidates[1].score < 0.15)
    : candidates[0].score < 0.2;
  return {
    candidates,
    uncertain,
    attribution: { name: "Pl@ntNet", url: "https://plantnet.org/" },
  };
}

const NEARBY_RADIUS_KM_MAX = 50;
const NEARBY_MIN_RADIUS_KM = 1;

const NEARBY_PHOTO_HOSTS = [
  "inaturalist-open-data.s3.amazonaws.com",
  "static.inaturalist.org",
  "bs.plantnet.org",
];
const WIKI_BATCH = 3;

/** Only attribution-bearing Creative Commons images are shown; anything else becomes null. */
function nearbyPhoto(media: unknown): NearbyPhoto | null {
  if (!Array.isArray(media)) return null;
  for (const entry of media) {
    if (!record(entry) || entry.type !== "StillImage") continue;
    const identifier = nonemptyName(entry.identifier);
    const license = nonemptyName(entry.license);
    const credit = nonemptyName(entry.rightsHolder) ?? nonemptyName(entry.publisher);
    if (!identifier || !license || !credit) continue;
    if (license.length > 120 || credit.length > 120) continue;
    let parsed: URL;
    try {
      parsed = new URL(identifier);
    } catch {
      continue;
    }
    if (parsed.protocol !== "https:" || !NEARBY_PHOTO_HOSTS.includes(parsed.hostname)) continue;
    // iNaturalist serves size variants by filename stem; other hosts keep the original.
    const sizeVariant = (size: string): URL => parsed.hostname.endsWith("amazonaws.com")
      ? new URL(identifier.replace(/\/(original|large|medium|small)\.(jpe?g|png)$/i, `/${size}.$2`))
      : parsed;
    return {
      thumbUrl: sizeVariant("small").toString(),
      url: sizeVariant("medium").toString(),
      // Creative Commons URLs are stored without a scheme so the app can display them verbatim.
      license: /creativecommons\.org\/(?:licenses|publicdomain)\//i.test(license)
        ? license.replace(/^https?:\/\//i, "").replace(/\/$/, "")
        : license,
      credit,
    };
  }
  return null;
}

interface NearbyWiki {
  commonName: string;
  description: string | null;
  wikiUrl: string;
  /** Only set for articles whose lead names the species, so the picture matches the plant. */
  thumbnail: string | null;
  /** Compact Creative Commons label, or null when the file has no verifiable CC licence. */
  license: string | null;
  credit: string;
}

/**
 * Indonesian name and summary from Wikipedia, accepted only when the article's lead
 * actually contains the scientific name, which rejects unrelated name collisions.
 */
async function nearbyWiki(scientificName: string, signal: AbortSignal): Promise<NearbyWiki | null> {
  const url = new URL("https://id.wikipedia.org/w/api.php");
  url.search = new URLSearchParams({
    action: "query", generator: "search", gsrsearch: `"${scientificName}"`, gsrlimit: "1",
    prop: "extracts|description|pageimages|imageinfo", exintro: "1", explaintext: "1", exsentences: "2",
    piprop: "thumbnail", pithumbsize: "640", iiprop: "extmetadata",
    format: "json", formatversion: "2", redirects: "1",
  }).toString();
  try {
    const response = await fetch(url, {
      signal, redirect: "manual",
      headers: { "User-Agent": "JagaDaun/0.7 (family plant app)", Accept: "application/json" },
    });
    if (!response.ok) {
      cancelBody(response.body);
      return null;
    }
    const body: unknown = await response.json();
    const pages = record(body) && record(body.query) && Array.isArray(body.query.pages)
      ? body.query.pages : [];
    const page = pages[0];
    if (!record(page)) return null;
    const title = nonemptyName(page.title);
    const extract = typeof page.extract === "string" ? page.extract : "";
    if (!title || !extract || !extract.toLowerCase().includes(scientificName.toLowerCase())) return null;
    const description = nonemptyName(page.description);
    const thumbnail = record(page.thumbnail) ? nonemptyName(page.thumbnail.source) : null;
    // The real Commons licence must travel with the picture; a generic label cannot be shown.
    const info = Array.isArray(page.imageinfo) ? page.imageinfo[0] : null;
    const metadata = record(info) && record(info.extmetadata) ? info.extmetadata : null;
    const licenseName = metadata && record(metadata.LicenseShortName) &&
      typeof metadata.LicenseShortName.value === "string" ? metadata.LicenseShortName.value.trim() : "";
    const artist = metadata && record(metadata.Artist) &&
      typeof metadata.Artist.value === "string" ? metadata.Artist.value.replace(/<[^>]*>/g, "").trim() : "";
    // Only a Creative Commons file may be shown next to the plant name.
    const creativeCommons = /^cc[- ]?by/i.test(licenseName) || /creativecommons\.org/i.test(licenseName);
    return {
      commonName: title,
      description: description ? description.slice(0, 160) : null,
      wikiUrl: `https://id.wikipedia.org/wiki/${encodeURIComponent(title.replace(/ /g, "_"))}`,
      thumbnail: thumbnail && thumbnail.startsWith("https://") ? thumbnail : null,
      license: creativeCommons ? licenseName.slice(0, 120) : null,
      credit: (artist || "Wikipedia (id)").slice(0, 120),
    };
  } catch {
    // Enrichment is optional: a Wikipedia failure must never fail the location search.
    return null;
  }
}

/** Small batches stay under Wikipedia's anonymous rate limit. */
async function nearbyWikiBatch(names: string[], signal: AbortSignal): Promise<Map<string, NearbyWiki | null>> {
  const results = new Map<string, NearbyWiki | null>();
  for (let index = 0; index < names.length; index += WIKI_BATCH) {
    const slice = names.slice(index, index + WIKI_BATCH);
    const settled = await Promise.all(slice.map(name => nearbyWiki(name, signal)));
    slice.forEach((name, offset) => results.set(name, settled[offset]));
  }
  return results;
}

/**
 * A species may have no licensed photo inside the searched area, so one extra bounded
 * request looks for a human observation of the same species anywhere.
 */
async function nearbySpeciesPhoto(speciesKey: string, signal: AbortSignal): Promise<NearbyPhoto | null> {
  const url = new URL("https://api.gbif.org/v1/occurrence/search");
  url.searchParams.set("speciesKey", speciesKey);
  url.searchParams.set("mediaType", "StillImage");
  url.searchParams.set("basisOfRecord", "HUMAN_OBSERVATION");
  url.searchParams.set("occurrenceStatus", "PRESENT");
  url.searchParams.set("limit", "5");
  try {
    const response = await fetch(url, {
      signal, redirect: "manual",
      headers: { "User-Agent": "JagaDaun/0.7 (family plant app)", Accept: "application/json" },
    });
    if (!response.ok) {
      cancelBody(response.body);
      return null;
    }
    const body: unknown = await response.json();
    const results = record(body) && Array.isArray(body.results) ? body.results : [];
    for (const candidate of results) {
      if (!record(candidate)) continue;
      const photo = nearbyPhoto(candidate.media);
      if (photo) return photo;
    }
    return null;
  } catch {
    // Optional enrichment: never let a photo lookup fail the location response.
    return null;
  }
}

/** GBIF facets rank all matching records, not a truncated page of observations. */
async function nearbySpecies(lat: number, lon: number, radiusKm: number) {
  return deadline(PROVIDER_DEADLINE_MS, async signal => {
    const base = new URL("https://api.gbif.org/v1/occurrence/search");
    base.searchParams.set("geoDistance", `${lat.toFixed(4)},${lon.toFixed(4)},${radiusKm * 1000}`);
    base.searchParams.set("kingdomKey", "6"); // Plantae
    base.searchParams.set("hasCoordinate", "true");
    base.searchParams.set("hasGeospatialIssue", "false");
    base.searchParams.set("occurrenceStatus", "PRESENT");
    const options: RequestInit & { cf: { cacheTtl: number; cacheEverything: boolean } } = {
      signal,
      redirect: "manual",
      headers: { "User-Agent": "JagaDaun/0.7 (family plant app)", Accept: "application/json" },
      cf: { cacheTtl: 600, cacheEverything: true },
    };
    const read = async (url: URL): Promise<Record<string, unknown>> => {
      const response = await fetch(url, options);
      if (!response.ok) {
        cancelBody(response.body);
        if (response.status === 429) throw new RequestError("PROVIDER_QUOTA", 429);
        throw new RequestError("PROVIDER_UNAVAILABLE", 502);
      }
      const body: unknown = await response.json();
      if (!record(body)) throw new RequestError("PROVIDER_UNAVAILABLE", 502);
      return body;
    };
    const ranking = new URL(base);
    ranking.searchParams.set("limit", "0");
    ranking.searchParams.set("facet", "speciesKey");
    ranking.searchParams.set("facetLimit", "12");
    const body = await read(ranking);
    if (typeof body.count !== "number" || !Number.isSafeInteger(body.count) || body.count < 0 ||
        !Array.isArray(body.facets)) throw new RequestError("PROVIDER_UNAVAILABLE", 502);
    const facet = body.facets.find(value => record(value) && value.field === "SPECIES_KEY");
    if (body.count === 0) return { species: [], totalRecords: 0 };
    if (!record(facet) || !Array.isArray(facet.counts)) throw new RequestError("PROVIDER_UNAVAILABLE", 502);
    const leaders = facet.counts.slice(0, 12);
    const species = await Promise.all(leaders.map(async value => {
      if (!record(value) || typeof value.name !== "string" || !/^\d+$/.test(value.name) ||
          typeof value.count !== "number" || !Number.isSafeInteger(value.count) || value.count <= 0) {
        throw new RequestError("PROVIDER_UNAVAILABLE", 502);
      }
      // One representative record supplies the species name, map point, and photo.
      // A media filter can return nothing for a species that does have occurrences,
      // so the first request keeps media and the second runs only when it came back empty.
      const detail = new URL(base);
      detail.searchParams.set("speciesKey", value.name);
      detail.searchParams.set("limit", "3");
      detail.searchParams.set("mediaType", "StillImage");
      let occurrences = await read(detail);
      let results = Array.isArray(occurrences.results) ? occurrences.results : [];
      if (results.length === 0) {
        detail.searchParams.delete("mediaType");
        occurrences = await read(detail);
        results = Array.isArray(occurrences.results) ? occurrences.results : [];
      }
      // A media filter can return nothing for a species that does have occurrences, so
      // the media request is retried without it instead of failing the whole response.
      let scientificName: string | null = null;
      let latitude = Number.NaN;
      let longitude = Number.NaN;
      let photo: NearbyPhoto | null = null;
      for (const candidate of results) {
        if (!record(candidate)) continue;
        const name = nonemptyName(candidate.species);
        const lat = candidate.decimalLatitude;
        const lon = candidate.decimalLongitude;
        if (!name || typeof lat !== "number" || typeof lon !== "number" ||
            !Number.isFinite(lat) || !Number.isFinite(lon) ||
            Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
        scientificName ??= name;
        if (!Number.isFinite(latitude)) {
          latitude = lat;
          longitude = lon;
        }
        photo ??= nearbyPhoto(candidate.media);
      }
      if (!scientificName || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        throw new RequestError("PROVIDER_UNAVAILABLE", 502);
      }
      return {
        speciesKey: value.name,
        scientificName,
        legacyCommonName: nonemptyName(results.find(record)?.vernacularName),
        observations: value.count,
        latitude,
        longitude,
        photo,
      };
    }));
    species.sort((a, b) => b.observations - a.observations || a.scientificName.localeCompare(b.scientificName));
    // Area media first, then one bounded per-species lookup, then a verified wiki picture.
    const fallbackPhotos = await Promise.all(species.map(entry =>
      entry.photo ? Promise.resolve(null) : nearbySpeciesPhoto(entry.speciesKey, signal)));
    const wiki = await nearbyWikiBatch(species.map(entry => entry.scientificName), signal);
    return {
      species: species.map(({ legacyCommonName, speciesKey: _, ...entry }, index) => {
        const page = wiki.get(entry.scientificName);
        // The Commons licence and author must be shown, so a file without a CC licence is skipped.
        const wikiPhoto = page?.thumbnail && page.license
          ? { thumbUrl: page.thumbnail, url: page.thumbnail, license: page.license, credit: page.credit }
          : null;
        return {
          ...entry,
          photo: entry.photo ?? fallbackPhotos[index] ?? wikiPhoto,
          commonName: page?.commonName ?? legacyCommonName,
          description: page?.description ?? null,
          wikiUrl: page?.wikiUrl ?? null,
        } satisfies NearbySpecies;
      }),
      totalRecords: body.count,
    };
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const path = new URL(request.url).pathname;
    if (path === "/health" && request.method === "GET") {
      return configured(env) ? json({ status: "ok" }) : json({ status: "unavailable" }, 503);
    }
    const healthCheck = path === "/v1/health-check";
    const analysis = path === "/v1/analyze";
    const nearby = path === "/v1/nearby";
    if (path !== "/v1/identify" && !healthCheck && !analysis && !nearby) return errorResponse("NOT_FOUND", 404);
    if (request.method !== "POST") return json({ error: { code: "METHOD_NOT_ALLOWED" } }, 405, { Allow: "POST" });
    if (!configured(env)) return errorResponse("NOT_CONFIGURED", 503);
    try {
      if (!await authorized(request, env.FAMILY_TOKEN)) return errorResponse("UNAUTHORIZED", 401);
      const device = request.headers.get("X-Device-Id") ?? "";
      if (!UUID.test(device) || (!healthCheck && !analysis && !nearby &&
          request.headers.get("Content-Type")?.split(";", 1)[0].trim().toLowerCase() !== "image/jpeg")) {
        return errorResponse("INVALID_IMAGE", 400);
      }
      if (nearby) {
        // Location search is free, so it never consumes an identification quota slot.
        const parameters = new URL(request.url).searchParams;
        // Missing or blank coordinates must not be coerced to the valid coordinate zero.
        const rawLat = parameters.get("lat");
        const rawLon = parameters.get("lon");
        const lat = rawLat?.trim() ? Number(rawLat) : Number.NaN;
        const lon = rawLon?.trim() ? Number(rawLon) : Number.NaN;
        const radius = Number(parameters.get("radius") ?? "10");
        if (!Number.isFinite(lat) || !Number.isFinite(lon) ||
            Math.abs(lat) > 90 || Math.abs(lon) > 180 ||
            !Number.isFinite(radius) || radius <= 0) {
          return errorResponse("INVALID_LOCATION", 400);
        }
        const radiusKm = Math.round(Math.min(Math.max(radius, NEARBY_MIN_RADIUS_KM), NEARBY_RADIUS_KM_MAX));
        const result = await nearbySpecies(lat, lon, radiusKm);
        return json({
          ...result,
          radiusKm,
          attribution: { name: "GBIF", url: "https://www.gbif.org/" },
        });
      }
      const images = healthCheck || analysis ? await readMultipartImages(request, MAX_HEALTH_IMAGES)
        : [new Blob([await readImage(request)], { type: "image/jpeg" })];
      try {
        if (!await reserveQuota(env.DB, device.toLowerCase())) return errorResponse("DAILY_LIMIT", 429);
      } catch (error) {
        if (error instanceof RequestError) throw error;
        throw new RequestError("PROVIDER_UNAVAILABLE", 503);
      }
      if (analysis) {
        // Settle independently so one provider's failure cannot discard the other's hypotheses.
        const [identification, health] = await Promise.allSettled([
          mappedProviderResults(images, env.PLANTNET_API_KEY, false),
          mappedProviderResults(images, env.PLANTNET_API_KEY, true),
        ]);
        if (identification.status === "rejected" && health.status === "rejected") {
          // Deterministic precedence retains the existing safe identification HTTP error contract.
          throw identification.reason;
        }
        return json({
          identification: identification.status === "fulfilled" ? identification.value : null,
          identificationErrorCode: identification.status === "rejected"
            ? identification.reason instanceof RequestError ? identification.reason.code : "PROVIDER_UNAVAILABLE"
            : null,
          health: health.status === "fulfilled" ? health.value : null,
          healthErrorCode: health.status === "rejected"
            ? health.reason instanceof RequestError ? health.reason.code : "PROVIDER_UNAVAILABLE"
            : null,
        });
      }
      return json(await mappedProviderResults(images, env.PLANTNET_API_KEY, healthCheck));
    } catch (error) {
      // Deliberately omit exceptions, upstream bodies, URLs, and image/name data from logs.
      if (error instanceof RequestError) return errorResponse(error.code, error.status);
      return errorResponse("PROVIDER_UNAVAILABLE", 502);
    }
  },
};
