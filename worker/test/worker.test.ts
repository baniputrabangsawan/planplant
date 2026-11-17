import assert from "node:assert/strict";
import { timingSafeEqual } from "node:crypto";
import { afterEach, test } from "node:test";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import worker, { type D1Database, type D1PreparedStatement, type Env } from "../src/worker.ts";

// Cloudflare's native constant-time extension is not exposed by Node's SubtleCrypto.
Object.defineProperty(crypto.subtle, "timingSafeEqual", {
  configurable: true,
  value: (a: ArrayBuffer, b: ArrayBuffer) => timingSafeEqual(new Uint8Array(a), new Uint8Array(b)),
});

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0xff, 0xd9]);
const DEVICE = "6a9d0732-926c-41ef-95a0-e2fa5efc013a";
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

interface FakeStatement extends D1PreparedStatement {
  sql: string;
  values: unknown[];
}

class FakeDatabase implements D1Database {
  rows: { request: string; day: string; device: string }[] = [];
  fail = false;
  batches = 0;
  reservationSql = "";
  prepare(sql: string): FakeStatement {
    return { sql, values: [] as unknown[], bind(...values: unknown[]) { return { ...this, values }; } };
  }
  async batch(statements: D1PreparedStatement[]) {
    this.batches++;
    if (this.fail) throw new Error("database unavailable");
    return statements.map(statement => {
      const { sql, values } = statement as FakeStatement;
      if (sql.startsWith("DELETE")) {
        const before = this.rows.length;
        this.rows = this.rows.filter(row => row.day >= String(values[0]));
        return { success: true, meta: { changes: before - this.rows.length } };
      }
      this.reservationSql = sql;
      assert.match(sql, /INSERT INTO identification_requests/);
      assert.match(sql, /COUNT\(\*\).*day = \?\) < 100/s);
      assert.match(sql, /COUNT\(\*\).*day = \? AND device = \?\) < 20/s);
      const [request, day, device, globalDay, deviceDay, deviceHash] = values.map(String);
      assert.equal(day, globalDay);
      assert.equal(day, deviceDay);
      assert.equal(device, deviceHash);
      if (this.rows.filter(row => row.day === day).length >= 100 ||
          this.rows.filter(row => row.day === day && row.device === device).length >= 20) {
        return { success: true, meta: { changes: 0 } };
      }
      this.rows.push({ request, day, device });
      return { success: true, meta: { changes: 1 } };
    });
  }
}

function environment(db = new FakeDatabase()): Env {
  return { FAMILY_TOKEN: "test-family-token", PLANTNET_API_KEY: "test-provider-key", DB: db };
}

function request(body: BodyInit = JPEG, overrides: Record<string, string> = {}, path = "/v1/identify") {
  return new Request(`https://identify.example${path}`, {
    method: "POST",
    headers: { authorization: "Bearer test-family-token", "content-type": "image/jpeg", "x-device-id": DEVICE, ...overrides },
    body,
    duplex: "half",
  } as RequestInit);
}

function healthForm(images: Uint8Array<ArrayBuffer>[] = [JPEG]): FormData {
  const form = new FormData();
  for (const [index, image] of images.entries()) {
    form.append("images", new Blob([image], { type: "image/jpeg" }), `plant-${index + 1}.jpg`);
  }
  return form;
}

function healthRequest(body: BodyInit = healthForm(), overrides: Record<string, string> = {}) {
  const multipart = body instanceof Uint8Array ? healthForm([body]) : body;
  return new Request("https://identify.example/v1/health-check", {
    method: "POST",
    headers: { authorization: "Bearer test-family-token", "x-device-id": DEVICE, ...overrides },
    body: multipart,
    duplex: "half",
  } as RequestInit);
}

function upstream(body: unknown, status = 200) {
  let calls = 0;
  globalThis.fetch = async () => { calls++; return Response.json(body, { status }); };
  return () => calls;
}

function species(scientificName: string, score: number, commonNames: unknown = []) {
  return { score, species: { scientificNameWithoutAuthor: scientificName, commonNames } };
}

async function expectError(response: Response, status: number, code: string) {
  assert.equal(response.status, status);
  assert.deepEqual(await response.json(), { error: { code } });
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
}

test("unauthorized requests never reserve quota or call the provider", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  for (const authorization of ["", "Bearer wrong", "Basic test-family-token"]) {
    await expectError(await worker.fetch(request(JPEG, { authorization }), environment(db)), 401, "UNAUTHORIZED");
  }
  assert.equal(calls(), 0);
  assert.equal(db.rows.length, 0);
  assert.equal(db.batches, 0);
});

test("health exposes readiness only, without credentials", async () => {
  const calls = upstream({});
  for (const [env, status, ready] of [[environment(), 200, "ok"], [{}, 503, "unavailable"]] as const) {
    const response = await worker.fetch(new Request("https://identify.example/health"), env);
    assert.equal(response.status, status);
    assert.deepEqual(await response.json(), { status: ready });
  }
  assert.equal(calls(), 0);
});

test("unconfigured identification fails closed", async () => {
  const calls = upstream({});
  await expectError(await worker.fetch(request(), {}), 503, "NOT_CONFIGURED");
  assert.equal(calls(), 0);
});

test("rejects announced and streamed oversized uploads before quota", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  await expectError(await worker.fetch(request(JPEG, { "content-length": String(4 * 1024 * 1024 + 1) }), environment(db)), 413, "IMAGE_TOO_LARGE");
  let cancellations = 0;
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new Uint8Array(4 * 1024 * 1024));
      controller.enqueue(new Uint8Array(1));
    },
    cancel() { cancellations++; },
  });
  await expectError(await worker.fetch(request(stream), environment(db)), 413, "IMAGE_TOO_LARGE");
  assert.equal(cancellations, 1);
  assert.equal(db.rows.length, 0);
  assert.equal(db.batches, 0);
  assert.equal(calls(), 0);
});

test("validates UUID, media type, JPEG signature, and truncation before quota", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  for (const invalid of [request(JPEG, { "x-device-id": "not-a-uuid" }), request(JPEG, { "content-type": "image/png" }), request(new Uint8Array([1, 2, 3, 4])), request(JPEG.slice(0, 4))]) {
    await expectError(await worker.fetch(invalid, environment(db)), 400, "INVALID_IMAGE");
  }
  assert.equal(db.rows.length, 0);
  assert.equal(db.batches, 0);
  assert.equal(calls(), 0);
});

test("maps, sorts, deduplicates, filters, and limits candidates; sends only JPEG multipart", async () => {
  let calls = 0;
  globalThis.fetch = async (input, init) => {
    calls++;
    const url = new URL(String(input));
    assert.equal(url.origin + url.pathname, "https://my-api.plantnet.org/v2/identify/all");
    assert.equal(url.searchParams.get("api-key"), "test-provider-key");
    assert.equal(url.searchParams.get("lang"), "id");
    assert.equal(url.searchParams.get("nb-results"), "3");
    assert.equal(url.searchParams.get("include-related-images"), "false");
    // Workers supports manual/follow, not the browser-standard "error" mode.
    assert.equal(init?.redirect, "manual");
    assert.equal(init?.method, "POST");
    const form = init?.body as FormData;
    assert.equal(form.get("organs"), "auto");
    const image = form.get("images") as File;
    assert.equal(image.type, "image/jpeg");
    assert.deepEqual(new Uint8Array(await image.arrayBuffer()), JPEG);
    assert.deepEqual([...form.keys()].sort(), ["images", "organs"]);
    return Response.json({ results: [
      species(" D species ", 0.1), species("B species", 0.7, ["", 4, " Common B "]),
      species("A species", 0.9, ["Common A"]), species("a SPECIES", 0.8),
      species("C species", 0.3), species("Bad score", 1.1), species("", 0.6),
      { score: 0.4, species: null }, null,
    ] });
  };
  const db = new FakeDatabase();
  const response = await worker.fetch(request(), environment(db));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    candidates: [
      { scientificName: "A species", commonName: "Common A", score: 0.9 },
      { scientificName: "B species", commonName: "Common B", score: 0.7 },
      { scientificName: "C species", commonName: null, score: 0.3 },
    ],
    uncertain: false,
    attribution: { name: "Pl@ntNet", url: "https://plantnet.org/" },
  });
  assert.equal(calls, 1);
  assert.equal(db.rows.length, 1);
  assert.match(db.rows[0].device, /^[0-9a-f]{64}$/);
  assert.notEqual(db.rows[0].device, DEVICE);
});

test("uncertain threshold uses strict less than 0.2", async () => {
  for (const [score, uncertain] of [[0, true], [0.199999, true], [0.2, false], [1, false]] as const) {
    upstream({ results: [species("Test species", score)] });
    const response = await worker.fetch(request(), environment());
    assert.equal(response.status, 200);
    assert.equal((await response.json()).uncertain, uncertain);
  }
});

test("empty or completely malformed results are not recognized", async () => {
  for (const results of [[], [species("Invalid", -0.1), species("Invalid", NaN), species("Invalid", "0.8" as unknown as number)]]) {
    const calls = upstream({ results });
    const db = new FakeDatabase();
    await expectError(await worker.fetch(request(), environment(db)), 422, "NOT_RECOGNIZED");
    assert.equal(db.rows.length, 1);
    assert.equal(calls(), 1);
  }
});

test("caps at 20 per device across Worker calls, canonicalizes UUID case, and counts provider failures", async () => {
  const calls = upstream({}, 500);
  const db = new FakeDatabase();
  const env = environment(db);
  for (let i = 0; i < 20; i++) {
    await expectError(await worker.fetch(request(JPEG, { "x-device-id": i % 2 ? DEVICE.toUpperCase() : DEVICE }), env), 502, "PROVIDER_UNAVAILABLE");
  }
  await expectError(await worker.fetch(request(), env), 429, "DAILY_LIMIT");
  assert.equal(calls(), 20);
  assert.equal(db.rows.length, 20);
});

test("caps at 100 globally, cleans records older than seven UTC days, and resets next UTC day", async (t) => {
  t.mock.timers.enable({ apis: ["Date"], now: new Date("2026-11-16T23:59:59Z") });
  const calls = upstream({ results: [species("Test species", 0.8)] });
  const db = new FakeDatabase();
  const day = new Date().toISOString().slice(0, 10);
  for (let i = 0; i < 100; i++) db.rows.push({ request: String(i), day, device: `other-${i}` });
  db.rows.push({ request: "old", day: "2000-01-01", device: "old" });
  await expectError(await worker.fetch(request(), environment(db)), 429, "DAILY_LIMIT");
  assert.equal(calls(), 0);
  assert.equal(db.rows.some(row => row.request === "old"), false);
  t.mock.timers.tick(1000);
  assert.equal((await worker.fetch(request(), environment(db))).status, 200);
  assert.equal(db.rows.length, 101);
});

test("database failure fails closed without any provider request", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  db.fail = true;
  await expectError(await worker.fetch(request(), environment(db)), 503, "PROVIDER_UNAVAILABLE");
  assert.equal(calls(), 0);
});

test("upstream status errors map safely with no automatic retries", async () => {
  for (const [status, expectedStatus, code] of [
    [401, 503, "PROVIDER_AUTH"], [403, 503, "PROVIDER_AUTH"], [429, 429, "PROVIDER_QUOTA"],
    [400, 502, "PROVIDER_UNAVAILABLE"], [404, 422, "NOT_RECOGNIZED"],
    [422, 422, "NOT_RECOGNIZED"], [500, 502, "PROVIDER_UNAVAILABLE"], [503, 502, "PROVIDER_UNAVAILABLE"],
  ] as const) {
    const calls = upstream({ secret: "must never be returned" }, status);
    await expectError(await worker.fetch(request(), environment()), expectedStatus, code);
    assert.equal(calls(), 1);
  }
});

test("malformed upstream JSON and network failures do not leak details", async () => {
  for (const fetch of [async () => new Response("not json"), async () => { throw new Error("url?api-key=secret"); }]) {
    globalThis.fetch = fetch;
    await expectError(await worker.fetch(request(), environment()), 502, "PROVIDER_UNAVAILABLE");
  }
});

test("upload has a total deadline and cancels a stalled reader", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const calls = upstream({});
  let canceled = false;
  const started = Promise.withResolvers<void>();
  const response = worker.fetch(request(new ReadableStream({
    pull() { started.resolve(); },
    cancel() { canceled = true; },
  }, { highWaterMark: 0 })), environment());
  await started.promise;
  t.mock.timers.tick(15_000);
  await expectError(await response, 504, "TIMEOUT");
  assert.equal(canceled, true);
  assert.equal(calls(), 0);
});

test("provider deadline also bounds a stalled response body", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const started = Promise.withResolvers<void>();
  globalThis.fetch = async (_input, init) => {
    started.resolve();
    return new Response(new ReadableStream({
      start(controller) { init?.signal?.addEventListener("abort", () => controller.error(new DOMException("Aborted", "AbortError"))); },
    }));
  };
  const pending = worker.fetch(request(), environment());
  await started.promise;
  t.mock.timers.tick(20_000);
  await expectError(await pending, 504, "TIMEOUT");
});

test("actual reservation SQL enforces device/global caps and permits the next UTC day", async () => {
  upstream({ results: [species("Test species", 0.8)] });
  const observed = new FakeDatabase();
  assert.equal((await worker.fetch(request(), environment(observed))).status, 200);
  const db = new DatabaseSync(":memory:");
  try {
    db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
    const insert = db.prepare(observed.reservationSql);
    const day = "2026-11-16";
    let requestId = 0;
    const reserve = (device: string, date = day) => insert.run(String(requestId++), date, device, date, date, device).changes;
    for (let device = 0; device < 5; device++) {
      for (let attempt = 0; attempt < 20; attempt++) assert.equal(reserve(`device-${device}`), 1);
      assert.equal(reserve(`device-${device}`), 0);
    }
    assert.equal(reserve("sixth-device"), 0);
    assert.equal(db.prepare("SELECT COUNT(*) AS count FROM identification_requests").get()?.count, 100);
    assert.equal(reserve("device-0", "2026-11-17"), 1);
  } finally {
    db.close();
  }
});

test("health check maps disease hypotheses, preserves unknown codes, and sends Indonesian JPEG multipart", async () => {
  let calls = 0;
  globalThis.fetch = async (input, init) => {
    calls++;
    const url = new URL(String(input));
    assert.equal(url.origin + url.pathname, "https://my-api.plantnet.org/v2/diseases/identify");
    assert.equal(url.searchParams.get("api-key"), "test-provider-key");
    assert.equal(url.searchParams.get("lang"), "id");
    assert.equal(url.searchParams.get("nb-results"), "3");
    assert.equal(url.searchParams.get("include-related-images"), "false");
    assert.equal(init?.redirect, "manual");
    assert.equal(init?.method, "POST");
    const form = init?.body as FormData;
    assert.deepEqual([...form.keys()].sort(), ["images", "organs"]);
    assert.equal(form.get("organs"), "auto");
    const image = form.get("images") as File;
    assert.equal(image.type, "image/jpeg");
    assert.deepEqual(new Uint8Array(await image.arrayBuffer()), JPEG);
    return Response.json({ results: [
      { name: "LOWWWW", description: "Low hypothesis", score: 0.1 },
      { name: " zzzzzz ", description: " Unknown provider hypothesis ", score: 0.9 },
      { name: "ZZZZZZ", description: "Duplicate lower score", score: 0.8 },
      { name: "PHYTOP", description: "Other hypothesis", score: 0.6 },
      { name: "ALTEAL", description: "Third hypothesis", score: 0.3 },
      { name: "", description: "Missing code", score: 1 },
      { name: "BLANKK", description: " ", score: 1 },
      { name: "BAD\u0000", description: "Invalid code", score: 1 },
      { name: "BADTXT", description: "Invalid\nlabel", score: 1 },
      { name: "BADNUM", description: "Score above one", score: 1.1 },
      { name: "BADNUM", description: "Negative score", score: -0.1 },
      { name: "BADNUM", description: "String score", score: "0.9" },
      { name: "BADNUM", description: "Nonfinite score", score: Infinity },
      { name: 42, description: "Numeric code", score: 1 },
      { name: "BADTXT", description: null, score: 1 },
      null,
    ] });
  };
  const db = new FakeDatabase();
  const response = await worker.fetch(healthRequest(), environment(db));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    candidates: [
      { code: "ZZZZZZ", label: "Unknown provider hypothesis", score: 0.9 },
      { code: "PHYTOP", label: "Other hypothesis", score: 0.6 },
      { code: "ALTEAL", label: "Third hypothesis", score: 0.3 },
    ],
    uncertain: false,
    attribution: { name: "Pl@ntNet", url: "https://plantnet.org/" },
  });
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(calls, 1);
  assert.equal(db.rows.length, 1);
});

test("health caution uses top score and the gap between distinct hypotheses", async () => {
  for (const [scores, uncertain] of [
    [[0], true], [[0.499999], true], [[0.5], false], [[1], false],
    [[0.8, 0.7], true], [[0.8, 0.6], false], [[0.65, 0.5], false],
    [[0.5, 0.5], true],
  ] as const) {
    upstream({ results: scores.map((score, index) => ({ name: `CODE0${index}`, description: `Hypothesis ${index}`, score })) });
    const response = await worker.fetch(healthRequest(), environment());
    assert.equal(response.status, 200);
    assert.equal((await response.json()).uncertain, uncertain);
  }
  upstream({ results: [
    { name: "SAMEEE", description: "Hypothesis", score: 0.8 },
    { name: "sameee", description: "Duplicate hypothesis", score: 0.79 },
  ] });
  assert.equal((await (await worker.fetch(healthRequest(), environment())).json()).uncertain, false);
});

test("empty or unusable disease results do not imply a healthy plant", async () => {
  for (const results of [[], [null, {}, { name: "CODEEE", description: "", score: 0.9 },
    { name: "", description: "Missing code", score: 0.9 },
    { name: "CODEEE", description: "Invalid score", score: null },
    { name: "CODEEE", description: "Invalid score", score: NaN }]]) {
    const calls = upstream({ results });
    const db = new FakeDatabase();
    await expectError(await worker.fetch(healthRequest(), environment(db)), 422, "NOT_RECOGNIZED");
    assert.equal(calls(), 1);
    assert.equal(db.rows.length, 1);
  }
});

test("malformed disease envelopes, JSON, and network errors are sanitized", async () => {
  for (const body of [null, {}, { results: {} }, { results: "not a list" }]) {
    const calls = upstream(body);
    await expectError(await worker.fetch(healthRequest(), environment()), 502, "PROVIDER_UNAVAILABLE");
    assert.equal(calls(), 1);
  }
  for (const fetch of [async () => new Response("secret invalid json"), async () => { throw new Error("url?api-key=secret"); }]) {
    globalThis.fetch = fetch;
    await expectError(await worker.fetch(healthRequest(), environment()), 502, "PROVIDER_UNAVAILABLE");
  }
});

test("disease provider errors including nonplants use shared sanitized codes without retries", async () => {
  for (const [status, expectedStatus, code] of [
    [401, 503, "PROVIDER_AUTH"], [403, 503, "PROVIDER_AUTH"], [429, 429, "PROVIDER_QUOTA"],
    [400, 502, "PROVIDER_UNAVAILABLE"], [404, 422, "NOT_RECOGNIZED"],
    [422, 422, "NOT_RECOGNIZED"], [302, 502, "PROVIDER_UNAVAILABLE"], [500, 502, "PROVIDER_UNAVAILABLE"],
  ] as const) {
    const calls = upstream({ secret: "must never be returned" }, status);
    await expectError(await worker.fetch(healthRequest(), environment()), expectedStatus, code);
    assert.equal(calls(), 1);
  }
});

test("health check rejects authorization and invalid multipart uploads before quota or upstream", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  let reads = 0;
  const unauthorized = healthRequest(new ReadableStream({
    pull() { reads++; },
  }, { highWaterMark: 0 }), {
    authorization: "Bearer wrong",
    "content-type": "multipart/form-data; boundary=health",
  });
  await expectError(await worker.fetch(unauthorized, environment(db)), 401, "UNAUTHORIZED");
  assert.equal(reads, 0);
  const unknownField = healthForm();
  unknownField.append("organs", "auto");
  const textImage = healthForm();
  textImage.append("images", "not a file");
  const unknownFile = healthForm();
  unknownFile.append("other", new Blob([JPEG], { type: "image/jpeg" }), "other.jpg");
  const png = new FormData();
  png.append("images", new Blob([JPEG], { type: "image/png" }), "plant.png");
  for (const invalid of [
    healthRequest(JPEG, { "x-device-id": "invalid" }),
    request(JPEG, {}, "/v1/health-check"),
    healthRequest(JPEG, { "content-type": "image/png" }),
    healthRequest(new Uint8Array([1, 2, 3, 4])),
    healthRequest(JPEG.slice(0, 4)),
    healthRequest(JPEG, { "content-length": "invalid" }),
    healthRequest(healthForm([])),
    healthRequest(healthForm([JPEG, JPEG, JPEG, JPEG])),
    healthRequest(unknownField),
    healthRequest(textImage),
    healthRequest(unknownFile),
    healthRequest(png),
    healthRequest("not multipart", { "content-type": "multipart/form-data; boundary=health" }),
    healthRequest("--health\r\n", { "content-type": "multipart/form-data; boundary=health" }),
    healthRequest(JPEG, { "content-type": "multipart/form-data" }),
    healthRequest(JPEG, { "content-type": `multipart/form-data; boundary=${"x".repeat(71)}` }),
  ]) {
    await expectError(await worker.fetch(invalid, environment(db)), 400, "INVALID_IMAGE");
  }
  const oversized = new Uint8Array(4 * 1024 * 1024 + 1);
  oversized.set(JPEG.subarray(0, 4));
  oversized.set([0xff, 0xd9], oversized.length - 2);
  await expectError(await worker.fetch(healthRequest(oversized), environment(db)), 413, "IMAGE_TOO_LARGE");
  const overhead = healthForm();
  overhead.append("images", new Blob([JPEG], { type: "image/jpeg" }), "x".repeat(16 * 1024));
  await expectError(await worker.fetch(healthRequest(overhead), environment(db)), 413, "IMAGE_TOO_LARGE");
  assert.equal(db.batches, 0);
  assert.equal(calls(), 0);
});

test("health upload bounds announced and streamed multipart bytes before parsing or quota", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  const maximum = 12 * 1024 * 1024 + 16 * 1024;
  await expectError(await worker.fetch(healthRequest(healthForm(), {
    "content-length": String(maximum + 1),
  }), environment(db)), 413, "IMAGE_TOO_LARGE");
  let canceled = false;
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new Uint8Array(maximum));
      controller.enqueue(new Uint8Array(1));
    },
    cancel() { canceled = true; },
  });
  await expectError(await worker.fetch(healthRequest(stream, {
    "content-type": "multipart/form-data; boundary=health",
  }), environment(db)), 413, "IMAGE_TOO_LARGE");
  assert.equal(canceled, true);
  assert.equal(db.batches, 0);
  assert.equal(calls(), 0);
});

test("health upload deadline cancels stalled multipart before quota", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const calls = upstream({});
  const db = new FakeDatabase();
  let canceled = false;
  const started = Promise.withResolvers<void>();
  const pending = worker.fetch(healthRequest(new ReadableStream({
    pull() { started.resolve(); },
    cancel() { canceled = true; },
  }, { highWaterMark: 0 }), {
    "content-type": "multipart/form-data; boundary=health",
  }), environment(db));
  await started.promise;
  t.mock.timers.tick(15_000);
  await expectError(await pending, 504, "TIMEOUT");
  assert.equal(canceled, true);
  assert.equal(db.batches, 0);
  assert.equal(calls(), 0);
});

test("health forwards all three JPEGs in order with auto organs and reserves quota once", async () => {
  const images = [JPEG, new Uint8Array([0xff, 0xd8, 0xff, 0xe1, 0xff, 0xd9]),
    new Uint8Array([0xff, 0xd8, 0xff, 0xe2, 0xff, 0xd9])];
  let calls = 0;
  globalThis.fetch = async (input, init) => {
    calls++;
    assert.equal(new URL(String(input)).pathname, "/v2/diseases/identify");
    const form = init?.body as FormData;
    assert.deepEqual([...form.keys()].sort(), ["images", "images", "images", "organs", "organs", "organs"]);
    assert.deepEqual(form.getAll("organs"), ["auto", "auto", "auto"]);
    const forwarded = form.getAll("images") as File[];
    assert.equal(forwarded.length, images.length);
    for (const [index, image] of forwarded.entries()) {
      assert.equal(image.type, "image/jpeg");
      assert.deepEqual(new Uint8Array(await image.arrayBuffer()), images[index]);
    }
    return Response.json({ results: [{ name: "CODEEE", description: "Hypothesis", score: 0.8 }] });
  };
  const db = new FakeDatabase();
  assert.equal((await worker.fetch(healthRequest(healthForm(images)), environment(db))).status, 200);
  assert.equal(calls, 1);
  assert.equal(db.batches, 1);
  assert.equal(db.rows.length, 1);
});

test("health accepts an image exactly at the four MiB limit", async () => {
  const image = new Uint8Array(4 * 1024 * 1024);
  image.set(JPEG.subarray(0, 4));
  image.set([0xff, 0xd9], image.length - 2);
  let calls = 0;
  globalThis.fetch = async (_input, init) => {
    calls++;
    const images = (init?.body as FormData).getAll("images") as File[];
    assert.equal(images.length, 1);
    assert.equal(images[0].size, image.length);
    assert.deepEqual(new Uint8Array(await images[0].arrayBuffer()), image);
    return Response.json({ results: [{ name: "CODEEE", description: "Hypothesis", score: 0.8 }] });
  };
  const db = new FakeDatabase();
  assert.equal((await worker.fetch(healthRequest(image), environment(db))).status, 200);
  assert.equal(calls, 1);
  assert.equal(db.rows.length, 1);
});

test("identification retains raw JPEG input and rejects health multipart before quota", async () => {
  const db = new FakeDatabase();
  const calls = upstream({ results: [species("Test species", 0.8)] });
  await expectError(await worker.fetch(new Request("https://identify.example/v1/identify", {
    method: "POST",
    headers: { authorization: "Bearer test-family-token", "x-device-id": DEVICE },
    body: healthForm(),
  }), environment(db)), 400, "INVALID_IMAGE");
  assert.equal(db.batches, 0);
  assert.equal(calls(), 0);
  assert.equal((await worker.fetch(request(), environment(db))).status, 200);
  assert.equal(db.rows.length, 1);
  assert.equal(calls(), 1);
});

test("health check fails closed on configuration or database failure and only accepts POST", async () => {
  const calls = upstream({});
  await expectError(await worker.fetch(healthRequest(), {}), 503, "NOT_CONFIGURED");
  const db = new FakeDatabase();
  db.fail = true;
  await expectError(await worker.fetch(healthRequest(), environment(db)), 503, "PROVIDER_UNAVAILABLE");
  const response = await worker.fetch(new Request("https://identify.example/v1/health-check"), environment());
  await expectError(response, 405, "METHOD_NOT_ALLOWED");
  assert.equal(response.headers.get("allow"), "POST");
  assert.equal(calls(), 0);
});

test("identification and disease attempts share one per-device daily quota, including failures", async () => {
  const calls = upstream({}, 500);
  const db = new FakeDatabase();
  const env = environment(db);
  for (let attempt = 0; attempt < 20; attempt++) {
    const imageRequest = attempt % 2 ? healthRequest(JPEG, { "x-device-id": DEVICE.toUpperCase() }) : request();
    await expectError(await worker.fetch(imageRequest, env), 502, "PROVIDER_UNAVAILABLE");
  }
  await expectError(await worker.fetch(request(), env), 429, "DAILY_LIMIT");
  await expectError(await worker.fetch(healthRequest(), env), 429, "DAILY_LIMIT");
  assert.equal(calls(), 20);
  assert.equal(db.rows.length, 20);
  assert.equal(new Set(db.rows.map(row => row.device)).size, 1);
});

test("identification and disease calls share the entire family global daily quota", async () => {
  let calls = 0;
  globalThis.fetch = async input => {
    calls++;
    return Response.json({ results: new URL(String(input)).pathname.includes("/diseases/")
      ? [{ name: "CODEEE", description: "Disease hypothesis", score: 0.8 }]
      : [species("Test species", 0.8)] });
  };
  const db = new FakeDatabase();
  const env = environment(db);
  for (let device = 0; device < 5; device++) {
    const headers = { "x-device-id": `6a9d0732-926c-41ef-95a0-e2fa5efc013${device}` };
    for (let attempt = 0; attempt < 20; attempt++) {
      assert.equal((await worker.fetch(attempt % 2 ? healthRequest(JPEG, headers) : request(JPEG, headers), env)).status, 200);
    }
  }
  const headers = { "x-device-id": "6a9d0732-926c-41ef-95a0-e2fa5efc013f" };
  await expectError(await worker.fetch(request(JPEG, headers), env), 429, "DAILY_LIMIT");
  await expectError(await worker.fetch(healthRequest(JPEG, headers), env), 429, "DAILY_LIMIT");
  assert.equal(calls, 100);
  assert.equal(db.rows.length, 100);
});

test("health provider deadline also bounds a stalled response body", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const started = Promise.withResolvers<void>();
  globalThis.fetch = async (_input, init) => {
    started.resolve();
    return new Response(new ReadableStream({
      start(controller) { init?.signal?.addEventListener("abort", () => controller.error(new DOMException("Aborted", "AbortError"))); },
    }));
  };
  const pending = worker.fetch(healthRequest(), environment());
  await started.promise;
  t.mock.timers.tick(20_000);
  await expectError(await pending, 504, "TIMEOUT");
});
