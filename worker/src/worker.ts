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
const MAX_HEALTH_IMAGES = 3;
const MAX_MULTIPART_OVERHEAD = 16 * 1024;
const MAX_HEALTH_UPLOAD_BYTES = MAX_HEALTH_IMAGES * MAX_IMAGE_BYTES + MAX_MULTIPART_OVERHEAD;
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

function validateMultipartFraming(bytes: Uint8Array, boundary: string): void {
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
    if (++parts > MAX_HEALTH_IMAGES) throw new RequestError("INVALID_IMAGE", 400);
    headerStart = after + 2;
  }
}

async function readHealthImages(request: Request): Promise<Blob[]> {
  // A short boundary and a bounded body must be established before formData allocates.
  const contentType = request.headers.get("Content-Type") ?? "";
  const multipart = /^multipart\/form-data\s*;\s*boundary=(?:"([0-9a-z'()+_,./:=?-]{1,70})"|([0-9a-z'()+_,./:=?-]{1,70}))\s*$/i.exec(contentType);
  if (!multipart) throw new RequestError("INVALID_IMAGE", 400);
  const boundary = multipart[1] ?? multipart[2];
  const length = request.headers.get("Content-Length");
  if (length !== null) {
    if (!/^\d+$/.test(length)) throw new RequestError("INVALID_IMAGE", 400);
    if (Number(length) > MAX_HEALTH_UPLOAD_BYTES) {
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
        const bytes = new Uint8Array(MAX_HEALTH_UPLOAD_BYTES);
        let size = 0;
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          if (value.byteLength > MAX_HEALTH_UPLOAD_BYTES - size) throw new RequestError("IMAGE_TOO_LARGE", 413);
          bytes.set(value, size);
          size += value.byteLength;
        }
        complete = true;
        validateMultipartFraming(bytes.subarray(0, size), boundary);
        const form = await new Response(bytes.subarray(0, size), {
          headers: { "Content-Type": contentType },
        }).formData();
        const images: Blob[] = [];
        let imageBytes = 0;
        for (const [name, value] of form.entries()) {
          if (name !== "images" || typeof value === "string" || value.type !== "image/jpeg" ||
              images.length >= MAX_HEALTH_IMAGES || value.size < 5) {
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

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const path = new URL(request.url).pathname;
    if (path === "/health" && request.method === "GET") {
      return configured(env) ? json({ status: "ok" }) : json({ status: "unavailable" }, 503);
    }
    const healthCheck = path === "/v1/health-check";
    if (path !== "/v1/identify" && !healthCheck) return errorResponse("NOT_FOUND", 404);
    if (request.method !== "POST") return json({ error: { code: "METHOD_NOT_ALLOWED" } }, 405, { Allow: "POST" });
    if (!configured(env)) return errorResponse("NOT_CONFIGURED", 503);
    try {
      if (!await authorized(request, env.FAMILY_TOKEN)) return errorResponse("UNAUTHORIZED", 401);
      const device = request.headers.get("X-Device-Id") ?? "";
      if (!UUID.test(device) || (!healthCheck &&
          request.headers.get("Content-Type")?.split(";", 1)[0].trim().toLowerCase() !== "image/jpeg")) {
        return errorResponse("INVALID_IMAGE", 400);
      }
      const images = healthCheck ? await readHealthImages(request)
        : [new Blob([await readImage(request)], { type: "image/jpeg" })];
      try {
        if (!await reserveQuota(env.DB, device.toLowerCase())) return errorResponse("DAILY_LIMIT", 429);
      } catch (error) {
        if (error instanceof RequestError) throw error;
        throw new RequestError("PROVIDER_UNAVAILABLE", 503);
      }
      const body = await providerResults(images, env.PLANTNET_API_KEY, healthCheck ? "diseases/identify" : "identify/all");
      const candidates = healthCheck ? healthCandidatesFrom(body) : candidatesFrom(body);
      if (candidates.length === 0) return errorResponse("NOT_RECOGNIZED", 422);
      // Disease candidates remain hypotheses at every score; this flag only adds display caution.
      const uncertain = healthCheck
        ? candidates[0].score < 0.5 || (candidates.length > 1 && candidates[0].score - candidates[1].score < 0.15)
        : candidates[0].score < 0.2;
      return json({
        candidates,
        uncertain,
        attribution: { name: "Pl@ntNet", url: "https://plantnet.org/" },
      });
    } catch (error) {
      // Deliberately omit exceptions, upstream bodies, URLs, and image/name data from logs.
      if (error instanceof RequestError) return errorResponse(error.code, error.status);
      return errorResponse("PROVIDER_UNAVAILABLE", 502);
    }
  },
};
