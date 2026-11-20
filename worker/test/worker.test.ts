import assert from "node:assert/strict";
import { timingSafeEqual } from "node:crypto";
import { afterEach, test } from "node:test";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { setImmediate as settleMicrotasks } from "node:timers/promises";
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

function analysisRequest(body: BodyInit = healthForm(), overrides: Record<string, string> = {}) {
  return new Request("https://identify.example/v1/analyze", {
    method: "POST",
    headers: { authorization: "Bearer test-family-token", "x-device-id": DEVICE, ...overrides },
    body,
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
    healthRequest(healthForm([JPEG, JPEG, JPEG, JPEG, JPEG, JPEG])),
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
  const maximum = 5 * 4 * 1024 * 1024 + 16 * 1024;
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

test("analysis starts both providers in parallel from one JPEG and reserves quota once", async () => {
  const started = new Set<string>();
  const bothStarted = Promise.withResolvers<void>();
  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    started.add(url.pathname);
    if (started.size === 2) bothStarted.resolve();
    await bothStarted.promise;
    assert.equal(url.origin, "https://my-api.plantnet.org");
    assert.equal(url.searchParams.get("lang"), "id");
    assert.equal(url.searchParams.get("nb-results"), "3");
    assert.equal(url.searchParams.get("include-related-images"), "false");
    assert.equal(url.searchParams.get("api-key"), "test-provider-key");
    assert.equal(init?.method, "POST");
    assert.equal(init?.redirect, "manual");
    assert.ok(init?.signal);
    const form = init?.body as FormData;
    assert.deepEqual([...form.keys()].sort(), ["images", "organs"]);
    assert.equal(form.get("organs"), "auto");
    const image = form.get("images") as File;
    assert.equal(image.type, "image/jpeg");
    assert.deepEqual(new Uint8Array(await image.arrayBuffer()), JPEG);
    return Response.json({ results: url.pathname.includes("/diseases/")
      ? [{ name: "ALTEAL", description: "Bercak daun", score: 0.65 },
        { name: "PHYTOP", description: "Hipotesis lain", score: 0.6 }]
      : [species("Epipremnum aureum", 0.19, ["Sirih gading"])] });
  };
  const db = new FakeDatabase();
  const response = await worker.fetch(analysisRequest(), environment(db));
  assert.equal(response.status, 200);
  const attribution = { name: "Pl@ntNet", url: "https://plantnet.org/" };
  assert.deepEqual(await response.json(), {
    identification: {
      candidates: [{ scientificName: "Epipremnum aureum", commonName: "Sirih gading", score: 0.19 }],
      uncertain: true, attribution,
    },
    identificationErrorCode: null,
    health: {
      candidates: [
        { code: "ALTEAL", label: "Bercak daun", score: 0.65 },
        { code: "PHYTOP", label: "Hipotesis lain", score: 0.6 },
      ],
      uncertain: true, attribution,
    },
    healthErrorCode: null,
  });
  assert.deepEqual([...started].sort(), ["/v2/diseases/identify", "/v2/identify/all"]);
  assert.equal(db.batches, 1);
  assert.equal(db.rows.length, 1);
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("analysis sends every rotation view to both providers for one family quota", async () => {
  const images = [JPEG, new Uint8Array([...JPEG.slice(0, -2), 1, 0xff, 0xd9]),
    new Uint8Array([...JPEG.slice(0, -2), 2, 0xff, 0xd9])];
  const seen = new Map<string, number[][]>();
  globalThis.fetch = async (input, init) => {
    const path = new URL(String(input)).pathname;
    const form = init?.body as FormData;
    const uploaded = await Promise.all(form.getAll("images").map(async value =>
      [...new Uint8Array(await (value as File).arrayBuffer())]));
    seen.set(path, uploaded);
    assert.deepEqual(form.getAll("organs"), ["auto", "auto", "auto"]);
    return Response.json({ results: path.includes("/diseases/")
      ? [{ name: "APHISP", description: "Aphis sp.", score: 0.8 }]
      : [species("Hibiscus rosa-sinensis", 0.9)] });
  };
  const db = new FakeDatabase();
  const response = await worker.fetch(analysisRequest(healthForm(images)), environment(db));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.identification.candidates[0].scientificName, "Hibiscus rosa-sinensis");
  assert.equal(body.health.candidates[0].code, "APHISP");
  assert.deepEqual([...seen.keys()].sort(), ["/v2/diseases/identify", "/v2/identify/all"]);
  for (const uploaded of seen.values()) assert.deepEqual(uploaded, images.map(image => [...image]));
  assert.equal(db.rows.length, 1);
});
test("one unrecognizable angle falls back to single photos instead of failing the whole plant", async () => {
  const images = [JPEG, new Uint8Array([...JPEG.slice(0, -2), 7, 0xff, 0xd9])];
  let calls = 0;
  globalThis.fetch = async (input, init) => {
    calls++;
    const form = init?.body as FormData;
    const uploaded = form.getAll("images").length;
    const diseased = new URL(String(input)).pathname.includes("/diseases/");
    // Batches containing the second angle are rejected; a lone first angle succeeds.
    if (uploaded > 1) return Response.json({}, { status: 422 });
    return Response.json({ results: diseased
      ? [{ name: "APHISP", description: "Aphis sp.", score: 0.8 }]
      : [species("Hibiscus rosa-sinensis", 0.9)] });
  };
  const db = new FakeDatabase();
  const response = await worker.fetch(analysisRequest(healthForm(images)), environment(db));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.identification.candidates[0].scientificName, "Hibiscus rosa-sinensis");
  assert.equal(body.health.candidates[0].code, "APHISP");
  // Two rejected batch calls, then one single-image call per provider.
  assert.equal(calls, 4);
  assert.equal(db.rows.length, 1);
});

test("fallback still reports not-recognized when every single angle is rejected", async () => {
  const db = new FakeDatabase();
  const calls = upstream({}, 422);
  await expectError(await worker.fetch(analysisRequest(healthForm([JPEG, JPEG])), environment(db)), 422, "NOT_RECOGNIZED");
  assert.equal(db.rows.length, 1);
  assert.equal(calls(), 6);
});

test("analysis preserves either successful section for every independent provider failure", async () => {
  for (const failedSection of ["identification", "health"] as const) {
    for (const [failure, code] of [
      [() => Response.json({}, { status: 401 }), "PROVIDER_AUTH"],
      [() => Response.json({}, { status: 429 }), "PROVIDER_QUOTA"],
      [() => Response.json({}, { status: 422 }), "NOT_RECOGNIZED"],
      [() => Response.json({ results: [] }), "NOT_RECOGNIZED"],
      [() => Response.json({ results: [null, { score: 1.1 }] }), "NOT_RECOGNIZED"],
      [() => Response.json({ results: {} }), "PROVIDER_UNAVAILABLE"],
      [() => new Response("private malformed provider response"), "PROVIDER_UNAVAILABLE"],
      [() => { throw new Error("private provider URL and credential"); }, "PROVIDER_UNAVAILABLE"],
    ] as const) {
      let calls = 0;
      globalThis.fetch = async input => {
        calls++;
        const section = new URL(String(input)).pathname.includes("/diseases/") ? "health" : "identification";
        if (section === failedSection) return failure();
        return Response.json({ results: section === "health"
          ? [{ name: "ALTEAL", description: "Bercak daun", score: 0.8 }]
          : [species("Epipremnum aureum", 0.8)] });
      };
      const db = new FakeDatabase();
      const response = await worker.fetch(analysisRequest(), environment(db));
      assert.equal(response.status, 200);
      const body = await response.json();
      const successfulSection = failedSection === "health" ? "identification" : "health";
      assert.equal(body[failedSection], null);
      assert.equal(body[`${failedSection}ErrorCode`], code);
      assert.equal(body[`${successfulSection}ErrorCode`], null);
      assert.equal(body[successfulSection].candidates.length, 1);
      assert.deepEqual(body[successfulSection].attribution, { name: "Pl@ntNet", url: "https://plantnet.org/" });
      assert.equal(calls, 2);
      assert.equal(db.rows.length, 1);
    }
  }
});

test("analysis with both sections failed uses the safe identification HTTP error without retry", async () => {
  for (const [status, expectedStatus, code] of [
    [401, 503, "PROVIDER_AUTH"], [429, 429, "PROVIDER_QUOTA"],
    [422, 422, "NOT_RECOGNIZED"], [500, 502, "PROVIDER_UNAVAILABLE"],
  ] as const) {
    const calls = upstream({ private: "must not leak" }, status);
    const db = new FakeDatabase();
    await expectError(await worker.fetch(analysisRequest(), environment(db)), expectedStatus, code);
    assert.equal(calls(), 2);
    assert.equal(db.rows.length, 1);
  }
  globalThis.fetch = async input => Response.json({}, {
    status: new URL(String(input)).pathname.includes("/diseases/") ? 429 : 401,
  });
  await expectError(await worker.fetch(analysisRequest(), environment()), 503, "PROVIDER_AUTH");
  upstream({ results: [] });
  await expectError(await worker.fetch(analysisRequest(), environment()), 422, "NOT_RECOGNIZED");
});

test("analysis rejects auth, invalid images and excess photos before quota or provider calls", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  let reads = 0;
  await expectError(await worker.fetch(analysisRequest(new ReadableStream({
    pull() { reads++; },
  }, { highWaterMark: 0 }), {
    authorization: "Bearer wrong",
    "content-type": "multipart/form-data; boundary=analysis",
  }), environment(db)), 401, "UNAUTHORIZED");
  assert.equal(reads, 0);
  const png = new FormData();
  png.append("images", new Blob([JPEG], { type: "image/png" }), "plant.png");
  const extraField = healthForm();
  extraField.append("organs", "auto");
  for (const invalid of [
    analysisRequest(healthForm(), { "x-device-id": "invalid" }),
    request(JPEG, {}, "/v1/analyze"),
    analysisRequest(healthForm([])),
    analysisRequest(healthForm([JPEG, JPEG, JPEG, JPEG, JPEG, JPEG])),
    analysisRequest(healthForm([JPEG.slice(0, 4)])),
    analysisRequest(png),
    analysisRequest(extraField),
    analysisRequest("not multipart", { "content-type": "multipart/form-data; boundary=analysis" }),
    analysisRequest(healthForm(), { "content-length": "invalid" }),
  ]) {
    await expectError(await worker.fetch(invalid, environment(db)), 400, "INVALID_IMAGE");
  }
  await expectError(await worker.fetch(analysisRequest(healthForm(), {
    "content-length": String(5 * 4 * 1024 * 1024 + 16 * 1024 + 1),
  }), environment(db)), 413, "IMAGE_TOO_LARGE");
  assert.equal(db.batches, 0);
  assert.equal(calls(), 0);
});

test("analysis shares quota with standalone calls and counts both-provider failure once", async () => {
  const calls = upstream({}, 500);
  const db = new FakeDatabase();
  const env = environment(db);
  for (let attempt = 0; attempt < 19; attempt++) {
    await expectError(await worker.fetch(attempt % 2 ? healthRequest() : request(), env), 502, "PROVIDER_UNAVAILABLE");
  }
  await expectError(await worker.fetch(analysisRequest(), env), 502, "PROVIDER_UNAVAILABLE");
  await expectError(await worker.fetch(analysisRequest(), env), 429, "DAILY_LIMIT");
  assert.equal(db.rows.length, 20);
  assert.equal(calls(), 21);
});

test("analysis preserves a success when the other provider response body times out", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const started = Promise.withResolvers<void>();
  globalThis.fetch = async (input, init) => {
    if (!new URL(String(input)).pathname.includes("/diseases/")) {
      return Response.json({ results: [species("Epipremnum aureum", 0.8)] });
    }
    started.resolve();
    return new Response(new ReadableStream({
      start(controller) { init?.signal?.addEventListener("abort", () => controller.error(new DOMException("Aborted", "AbortError"))); },
    }));
  };
  const pending = worker.fetch(analysisRequest(), environment());
  await started.promise;
  // Drain the successful response's body/microtasks before advancing both deadline timers.
  await settleMicrotasks();
  t.mock.timers.tick(20_000);
  const response = await pending;
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.identification.candidates[0].scientificName, "Epipremnum aureum");
  assert.equal(body.identificationErrorCode, null);
  assert.equal(body.health, null);
  assert.equal(body.healthErrorCode, "TIMEOUT");
});

test("analysis fails closed before providers on missing configuration, database failure, or wrong method", async () => {
  const calls = upstream({});
  const db = new FakeDatabase();
  await expectError(await worker.fetch(analysisRequest(), { FAMILY_TOKEN: "test-family-token", DB: db }), 503, "NOT_CONFIGURED");
  assert.equal(db.batches, 0);
  const response = await worker.fetch(new Request("https://identify.example/v1/analyze"), environment(db));
  await expectError(response, 405, "METHOD_NOT_ALLOWED");
  assert.equal(response.headers.get("allow"), "POST");
  assert.equal(db.batches, 0);
  db.fail = true;
  await expectError(await worker.fetch(analysisRequest(), environment(db)), 503, "PROVIDER_UNAVAILABLE");
  assert.equal(db.rows.length, 0);
  assert.equal(calls(), 0);
});

test("analysis returns the safe HTTP timeout when both providers exceed the deadline", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const bothStarted = Promise.withResolvers<void>();
  let calls = 0;
  globalThis.fetch = async (_input, init) => {
    if (++calls === 2) bothStarted.resolve();
    return new Response(new ReadableStream({
      start(controller) { init?.signal?.addEventListener("abort", () => controller.error(new DOMException("Aborted", "AbortError"))); },
    }));
  };
  const db = new FakeDatabase();
  const pending = worker.fetch(analysisRequest(), environment(db));
  await bothStarted.promise;
  t.mock.timers.tick(20_000);
  await expectError(await pending, 504, "TIMEOUT");
  assert.equal(calls, 2);
  assert.equal(db.rows.length, 1);
});

function nearbyRequest(url = "https://identify.example/v1/nearby?lat=-6.2&lon=106.816&radius=10") {
  return new Request(url, {
    method: "POST",
    headers: { authorization: "Bearer test-family-token", "x-device-id": DEVICE },
  });
}

function nearbyRanking(counts: { name: string; count: number }[] = [], count = 0) {
  return { count, results: [], facets: [{ field: "SPECIES_KEY", counts }] };
}

function wikiPage(title: string, scientificName: string, description: string | null = null) {
  return {
    query: { pages: [{ title, description, extract: `${scientificName} adalah tumbuhan tropis.` }] },
  };
}

const PHOTO = {
  type: "StillImage",
  identifier: "https://inaturalist-open-data.s3.amazonaws.com/photos/736703933/original.jpg",
  license: "http://creativecommons.org/licenses/by-nc/4.0/",
  rightsHolder: "reivawisdharilla",
  publisher: "iNaturalist",
};

test("nearby ranks all matching GBIF records without consuming identification quota", async () => {
  let calls = 0;
  globalThis.fetch = async input => {
    calls++;
    const url = new URL(String(input));
    assert.equal(url.origin, "https://api.gbif.org");
    assert.equal(url.pathname, "/v1/occurrence/search");
    assert.equal(url.searchParams.get("kingdomKey"), "6");
    assert.equal(url.searchParams.get("hasGeospatialIssue"), "false");
    assert.equal(url.searchParams.get("occurrenceStatus"), "PRESENT");
    assert.equal(url.searchParams.get("geoDistance"), "-6.2000,106.8160,10000");
    if (url.searchParams.get("facet") === "speciesKey") {
      assert.equal(url.searchParams.get("limit"), "0");
      assert.equal(url.searchParams.get("facetLimit"), "12");
      return Response.json(nearbyRanking([{ name: "1", count: 71 }, { name: "2", count: 42 }], 2859));
    }
    assert.equal(url.searchParams.get("limit"), "3");
    assert.equal(url.searchParams.get("mediaType"), "StillImage");
    const coconut = url.searchParams.get("speciesKey") === "1";
    return Response.json({ results: [{
      species: coconut ? "Cocos nucifera" : "Musa paradisiaca",
      vernacularName: coconut ? "Kelapa" : null,
      decimalLatitude: -6.21, decimalLongitude: 106.82,
      media: coconut ? [PHOTO] : [],
    }] });
  };
  const db = new FakeDatabase();
  const response = await worker.fetch(nearbyRequest(), environment(db));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(body.species.map((s: { scientificName: string; observations: number }) =>
    [s.scientificName, s.observations]), [["Cocos nucifera", 71], ["Musa paradisiaca", 42]]);
  assert.equal(body.species[0].commonName, "Kelapa");
  assert.equal(body.species[0].latitude, -6.21);
  assert.equal(body.radiusKm, 10);
  assert.equal(body.totalRecords, 2859);
  assert.deepEqual(body.attribution, { name: "GBIF", url: "https://www.gbif.org/" });
  assert.deepEqual(body.species[0].photo, {
    thumbUrl: "https://inaturalist-open-data.s3.amazonaws.com/photos/736703933/small.jpg",
    url: "https://inaturalist-open-data.s3.amazonaws.com/photos/736703933/medium.jpg",
    license: "creativecommons.org/licenses/by-nc/4.0",
    credit: "reivawisdharilla",
  });
  assert.equal(body.species[1].photo, null);
  assert.equal(db.rows.length, 0);
  assert.equal(db.batches, 0);
});

test("nearby only shows photos with verifiable attribution from allowed hosts", async () => {
  const cases = [
    { ...PHOTO, type: "MovingImage" },
    { ...PHOTO, license: null },
    { ...PHOTO, rightsHolder: null, publisher: null },
    { ...PHOTO, identifier: "http://inaturalist-open-data.s3.amazonaws.com/photos/1/original.jpg" },
    { ...PHOTO, identifier: "https://evil.example/photos/1/original.jpg" },
    { ...PHOTO, identifier: "https://inaturalist-open-data.s3.amazonaws.com.evil.example/1/original.jpg" },
  ];
  for (const media of cases) {
    globalThis.fetch = async input => {
      const url = new URL(String(input));
      return Response.json(url.searchParams.has("facet")
        ? nearbyRanking([{ name: "1", count: 2 }], 2)
        : { results: [{ species: "Cocos nucifera", decimalLatitude: -6.2, decimalLongitude: 106.8, media: [media] }] });
    };
    const body = await (await worker.fetch(nearbyRequest(), environment())).json();
    assert.equal(body.species[0].photo, null, JSON.stringify(media));
  }
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    if (url.searchParams.has("facet")) return Response.json(nearbyRanking([{ name: "1", count: 2 }], 2));
    return Response.json({ results: [{
      species: "Cocos nucifera", decimalLatitude: -6.2, decimalLongitude: 106.8,
      media: [{ ...PHOTO, identifier: "https://bs.plantnet.org/image/o/abc", license: "CC BY-SA", rightsHolder: "pratiwir" }],
    }] });
  };
  const body = await (await worker.fetch(nearbyRequest(), environment())).json();
  assert.equal(body.species[0].photo.url, "https://bs.plantnet.org/image/o/abc");
  assert.equal(body.species[0].photo.license, "CC BY-SA");
});

test("nearby adds Indonesian names only when Wikipedia confirms the scientific name", async () => {
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === "id.wikipedia.org") {
      const query = url.searchParams.get("gsrsearch") ?? "";
      if (query.includes("Musa paradisiaca")) return Response.json(wikiPage("Pisang nangka", "Musa paradisiaca"));
      // A hit whose lead does not mention the species must be rejected.
      return Response.json(wikiPage("Daun", "Tumbuhan ini memiliki daun hijau."));
    }
    if (url.searchParams.has("facet")) {
      return Response.json(nearbyRanking([{ name: "1", count: 9 }, { name: "2", count: 4 }], 20));
    }
    const first = url.searchParams.get("speciesKey") === "1";
    return Response.json({ results: [{
      species: first ? "Nephelium lappaceum" : "Musa paradisiaca",
      decimalLatitude: -6.21, decimalLongitude: 106.82,
    }] });
  };
  const body = await (await worker.fetch(nearbyRequest(), environment())).json();
  assert.equal(body.species[0].commonName, null);
  assert.equal(body.species[0].wikiUrl, null);
  assert.equal(body.species[0].description, null);
  assert.equal(body.species[1].commonName, "Pisang nangka");
  assert.equal(body.species[1].wikiUrl, "https://id.wikipedia.org/wiki/Pisang_nangka");
});

test("nearby still succeeds when Wikipedia enrichment fails or is rate limited", async () => {
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === "id.wikipedia.org") return new Response("too many", { status: 429 });
    if (url.searchParams.has("facet")) return Response.json(nearbyRanking([{ name: "1", count: 5 }], 5));
    return Response.json({ results: [{
      species: "Codiaeum variegatum", vernacularName: "Croton",
      decimalLatitude: -6.21, decimalLongitude: 106.82,
    }] });
  };
  const response = await worker.fetch(nearbyRequest(), environment());
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.species[0].commonName, "Croton");
  assert.equal(body.species[0].photo, null);
});

test("nearby falls back to a verified Wikipedia picture only with its real CC licence", async () => {
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === "id.wikipedia.org") {
      const name = (url.searchParams.get("gsrsearch") ?? "").replaceAll('"', "");
      const licensed = name === "Mimosa pudica";
      return Response.json({
        query: { pages: [{
          title: licensed ? "Putri malu" : "Puring",
          description: null,
          extract: `${name} adalah tumbuhan.`,
          thumbnail: { source: `https://upload.wikimedia.org/${name.replaceAll(" ", "_")}.jpg` },
          // The non-CC article must keep its photo hidden.
          imageinfo: licensed
            ? [{ extmetadata: {
              LicenseShortName: { value: "CC BY-SA 4.0" },
              Artist: { value: '<a href="https://commons.example">Budi</a>' },
            } }]
            : [{ extmetadata: { LicenseShortName: { value: "All rights reserved" } } }],
        }] },
      });
    }
    if (url.searchParams.has("facet")) {
      return Response.json(nearbyRanking([{ name: "1", count: 9 }, { name: "2", count: 4 }], 13));
    }
    if (url.searchParams.get("basisOfRecord") === "HUMAN_OBSERVATION") {
      return Response.json({ results: [] });
    }
    const first = url.searchParams.get("speciesKey") === "1";
    return Response.json({ results: [{
      species: first ? "Mimosa pudica" : "Codiaeum variegatum",
      decimalLatitude: -6.21, decimalLongitude: 106.82,
      media: first ? [] : [PHOTO],
    }] });
  };
  const body = await (await worker.fetch(nearbyRequest(), environment())).json();
  assert.deepEqual(body.species[0].photo, {
    thumbUrl: "https://upload.wikimedia.org/Mimosa_pudica.jpg",
    url: "https://upload.wikimedia.org/Mimosa_pudica.jpg",
    license: "CC BY-SA 4.0",
    credit: "Budi",
  });
  // A GBIF area photo wins over the Wikipedia fallback, even when the article is CC licensed.
  assert.equal(body.species[1].photo.credit, "reivawisdharilla");
});

test("nearby keeps occurrences when the media filter excludes every record", async () => {
  let unfilteredCalls = 0;
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === "id.wikipedia.org") return new Response("nope", { status: 503 });
    if (url.searchParams.has("facet")) {
      return Response.json(nearbyRanking([{ name: "3035652", count: 4 }], 4));
    }
    if (url.searchParams.get("mediaType") === "StillImage") {
      assert.equal(url.searchParams.get("limit"), "3");
      return Response.json({ count: 0, results: [] });
    }
    unfilteredCalls++;
    return Response.json({ count: 4, results: [{
      species: "Polyscias fruticosa", decimalLatitude: -6.21, decimalLongitude: 106.82,
    }] });
  };
  const response = await worker.fetch(nearbyRequest(), environment());
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(unfilteredCalls, 1);
  assert.equal(body.species[0].scientificName, "Polyscias fruticosa");
  assert.equal(body.species[0].observations, 4);
  assert.equal(body.species[0].photo, null);
});

test("nearby looks outside the area for a licensed species photo", async () => {
  const globalCalls: string[] = [];
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === "id.wikipedia.org") return new Response("nope", { status: 503 });
    if (url.searchParams.has("facet")) return Response.json(nearbyRanking([{ name: "3035652", count: 6 }], 6));
    if (url.searchParams.get("basisOfRecord") === "HUMAN_OBSERVATION") {
      globalCalls.push(url.searchParams.get("speciesKey") ?? "");
      assert.equal(url.searchParams.get("mediaType"), "StillImage");
      return Response.json({ results: [{ media: [PHOTO] }] });
    }
    return Response.json({ results: [{
      species: "Polyscias fruticosa", decimalLatitude: -6.21, decimalLongitude: 106.82,
    }] });
  };
  const body = await (await worker.fetch(nearbyRequest(), environment())).json();
  assert.deepEqual(globalCalls, ["3035652"]);
  assert.equal(body.species[0].photo.credit, "reivawisdharilla");
  assert.equal(body.species[0].photo.thumbUrl.endsWith("/small.jpg"), true);
});

test("nearby skips the global photo lookup when the area already supplied one", async () => {
  let globalCalls = 0;
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    if (url.hostname === "id.wikipedia.org") return new Response("nope", { status: 503 });
    if (url.searchParams.has("facet")) return Response.json(nearbyRanking([{ name: "1", count: 6 }], 6));
    if (url.searchParams.get("basisOfRecord") === "HUMAN_OBSERVATION") {
      globalCalls++;
      return Response.json({ results: [] });
    }
    return Response.json({ results: [{
      species: "Mimosa pudica", decimalLatitude: -6.21, decimalLongitude: 106.82, media: [PHOTO],
    }] });
  };
  const body = await (await worker.fetch(nearbyRequest(), environment())).json();
  assert.equal(globalCalls, 0);
  assert.equal(body.species[0].photo.credit, "reivawisdharilla");
});

test("nearby clamps radius and rejects missing, blank and unusable coordinates before provider calls", async () => {
  const calls = upstream(nearbyRanking());
  const db = new FakeDatabase();
  for (const query of [
    "lat=abc&lon=106", "lat=-6&lon=200", "lat=-6&lon=106&radius=0", "lon=106",
    "lat=-6", "lat=&lon=106", "lat=%20&lon=106", "lat=-6&lon=", "lat=Infinity&lon=0",
  ]) {
    await expectError(await worker.fetch(nearbyRequest(`https://identify.example/v1/nearby?${query}`), environment(db)), 400, "INVALID_LOCATION");
  }
  assert.equal(calls(), 0);
  globalThis.fetch = async input => {
    assert.equal(new URL(String(input)).searchParams.get("geoDistance"), "0.0000,0.0000,50000");
    return Response.json(nearbyRanking());
  };
  const body = await (await worker.fetch(nearbyRequest(
    "https://identify.example/v1/nearby?lat=0&lon=0&radius=900"), environment(db))).json();
  assert.equal(body.radiusKm, 50);
  assert.deepEqual(body.species, []);
  assert.equal(body.totalRecords, 0);
  assert.equal(db.rows.length, 0);
});

test("nearby requires authorization before requesting location records", async () => {
  const calls = upstream(nearbyRanking());
  const request = nearbyRequest();
  request.headers.set("Authorization", "Bearer wrong-token");
  await expectError(await worker.fetch(request, environment()), 401, "UNAUTHORIZED");
  assert.equal(calls(), 0);
});

test("nearby exposes safe upstream errors, never fabricating an empty map", async () => {
  for (const [status, code, expectedStatus] of [
    [429, "PROVIDER_QUOTA", 429], [503, "PROVIDER_UNAVAILABLE", 502],
  ] as const) {
    upstream({ secret: "must not escape" }, status);
    await expectError(await worker.fetch(nearbyRequest(), environment()), expectedStatus, code);
  }
  for (const malformed of [{ results: [] }, nearbyRanking([{ name: "bad", count: 5 }], 5)]) {
    upstream(malformed);
    await expectError(await worker.fetch(nearbyRequest(), environment()), 502, "PROVIDER_UNAVAILABLE");
  }
});

test("nearby rejects missing map coordinates rather than coercing null to zero", async () => {
  globalThis.fetch = async input => {
    const url = new URL(String(input));
    return Response.json(url.searchParams.has("facet")
      ? nearbyRanking([{ name: "1", count: 3 }], 3)
      : { results: [{ species: "Cocos nucifera", decimalLatitude: null, decimalLongitude: 106.8 }] });
  };
  await expectError(await worker.fetch(nearbyRequest(), environment()), 502, "PROVIDER_UNAVAILABLE");
});
