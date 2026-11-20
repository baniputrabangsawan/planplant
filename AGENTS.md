# Repository Guidelines

## Project Overview

Jaga Daun is a two-runtime repository: an Indonesian-language Android app (`:app`, Kotlin + Jetpack Compose) and one Cloudflare Worker (TypeScript) that holds provider credentials. The app identifies plants from photos, stores a private garden with reminders, saves health checks, and lists plants recorded near the user's location. Docs: `PRD (1).md` (scope, acceptance), `DESIGN.md` (UI, copy, a11y), `ARCHITECURE.md` (architecture, worker ops, QA protocol — the misspelling is the real filename).
## Architecture & Data Flow

- Android talks only to `https://jagadaun-identify…workers.dev`; the Worker calls Pl@ntNet (`/v1/identify`, `/v1/analyze`, `/v1/health-check`) and GBIF (`/v1/nearby`). `BuildConfig.IDENTIFICATION_URL` names the `/v1/identify` base; clients re-derive sibling paths. Cleartext traffic is disabled.
- Worker request order is a security invariant: route allowlist → POST only → `configured()` → Bearer auth (SHA-256 digests + `timingSafeEqual`) → device UUID + JPEG content type → bounded upload/deadline → quota reservation → provider call. Keep `reserveQuota` last.
- Quota is one atomic conditional D1 insert (20/device/day, 100/family/day, 7-day UTC purge). Failures deliberately consume quota; there are no refunds or automatic provider retries. `/v1/nearby` consumes no quota and never touches D1.
- Android dependency wiring is manual: `JagaDaunApplication` constructs `AppContainer`; screens receive it and read repositories/clients from it. No Hilt/Koin, no Ktor/OkHttp/Retrofit.
- Room (schema version 2) is the only structured storage; photos are app-private files referenced by absolute path. Drafts live in `cacheDir/photo-drafts|captures`; repositories promote a copy and commit the row inside a transaction, compensating (deleting the promoted copy, keeping the draft) on failure.
- Photo ownership is strict: the NavHost owns `draftPhotos`; a screen must never discard a saved reference or delete a file it does not own. Shared saved-photo cleanup must check both plant and health-record references (`deleteUnreferencedPhotos`). Any new photo-bearing table must join that check.
- UI is one `NavHost` with string routes; the bottom bar appears only on tab routes. Camera results reach other screens through `SavedStateHandle`, not through the container.
- Reminders: Room is authoritative, WorkManager is derived (`ReminderScheduler.sync`). Completion is a compare-and-set on the expected due time inside the transaction so repeat taps cannot double-log.
- Nearby keeps location in memory only: coarse/fused fix up to 10 minutes old, otherwise wait up to 20 s. Counts are frequencies of historical GBIF records — not abundance, not live presence, and not evidence of native or endemic status.

## Key Directories

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/jagadaun/app/` | `AppContainer`, `AppNavHost`, `MainActivity`; `ui/`, `data/`, `domain/`, `reminders/` |
| `app/src/main/java/com/jagadaun/app/ui/` | Compose screens by feature (`identify`, `garden`, `health`, `reminders`, `camera`, `nearby`, `home`, `components`, `theme`) |
| `app/src/main/java/com/jagadaun/app/data/` | Room (`local/`), repositories, `photos/PhotoStore`, `identification/` clients and parsers, `nearby/` |
| `app/src/main/java/com/jagadaun/app/domain/` | Pure Kotlin models and rules (no Android imports) |
| `app/src/test/`, `app/src/androidTest/` | JVM unit tests; device/Room/photo tests |
| `app/schemas/com.jagadaun.app.data.local.JagaDaunDatabase/` | Committed Room schema exports — versioned output, keep in git |
| `worker/src/worker.ts` | The entire Worker: routes, auth, upload bounds, quota, provider adapters |
| `worker/test/worker.test.ts` | Worker tests with faked `fetch`, fake D1, in-memory SQLite |
| `worker/schema.sql` | D1 quota table and index |

## Development Commands

Android (JDK 17 and an Android SDK; `local.properties` is not committed — set `ANDROID_HOME`). The Gradle daemon is disabled, so batch tasks into one invocation:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew testDebugUnitTest --tests com.jagadaun.app.domain.ReminderScheduleTest
./gradlew assembleDebugAndroidTest installDebug        # device must be in `adb devices` state
```

Device tests without erasing app data:

```bash
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.jagadaun.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.jagadaun.app.data.photos.PhotoPersistenceTest \
  com.jagadaun.app.test/androidx.test.runner.AndroidJUnitRunner
```

Worker (from `worker/`; Node ≥ 24, npm):

```bash
npm ci
npm test                      # node --test test/*.test.ts
npm run typecheck             # tsc --noEmit (src only)
npm run build                 # esbuild → dist/worker.js
node --test --test-name-pattern="nearby" test/worker.test.ts
```

`dist/worker.js` is gitignored and is the deploy entry point, so build before any deploy. `wrangler` is not a dependency; deploys happen outside the repository.

## Code Conventions & Common Patterns

- Kotlin: 4-space indent, trailing commas, `private companion object` constants near use, `internal` for UI-scoped symbols, Indonesian KDoc that explains *why* (privacy, cost, atomicity).
- ViewModels expose one `StateFlow` state object and guard each request with a `Job` plus a generation counter; results from superseded requests are dropped. Saves that must finish use `NonCancellable`, and cancellation is handled separately from failure so a retry never duplicates a record.
- Networking reuses the hand-written `HttpsURLConnection` shape: HTTPS only, no userinfo/query, redirects disabled, connect/read timeouts, bounded 64 KiB response read, `disconnect()` on cancellation, sanitized error codes. Add an endpoint this way instead of adding an HTTP library.
- Parsers are strict: lenient JSON off, unknown analysis fields rejected, attribution checked exactly, scores bounded, duplicates rejected. Map sanitized codes to Indonesian copy in the UI; never surface upstream bodies, photos, coordinates, or logs.
- Product thresholds are centralized (`MIN_NAMEABLE_SCORE = 0.30`, health uncertainty rules, Worker uncertainty gap). Changing one changes gating and copy, not just a number.
- Worker: return only `RequestError` codes; use `redirect: "manual"` (the `"error"` mode throws on Workers); keep observability disabled because upstream URLs can carry the provider key; validate multipart framing before parsing.
- Room changes are additive: add a `Migration`, bump `version`, register it, and commit the new schema JSON. No destructive fallback.
- UI copy is Indonesian and follows `DESIGN.md` (palette, type scale, 48 dp targets, 4.5:1 contrast, wrapping at 200% font). Read it before touching layouts, strings, or theme.

## Important Files

| File | Why it matters |
| --- | --- |
| `app/src/main/java/com/jagadaun/app/AppContainer.kt` | Single wiring point; construction order and mutation APIs |
| `app/src/main/java/com/jagadaun/app/AppNavHost.kt` | All routes, bottom bar, draft-photo ownership, camera handoff |
| `.../data/identification/IdentificationClient.kt` | Upload contract, 5-photo cap, JPEG 90/2048 px, error mapping |
| `.../data/identification/*Protocol.kt` | Strict response parsers and bounded reads |
| `.../data/photos/PhotoStore.kt` | Path confinement, import/promote/delete, 24 h draft sweep |
| `.../data/repository/PlantRepository.kt`, `HealthRecordRepository.kt` | Atomic save/rollback and shared-photo deletion |
| `.../data/local/JagaDaunDatabase.kt` | Version, migrations, singleton |
| `.../reminders/ReminderScheduler.kt` | Room→WorkManager sync and notification guards |
| `worker/src/worker.ts` | Routes, auth, quota, Pl@ntNet/GBIF adapters |
| `app/build.gradle.kts`, `worker/package.json`, `worker/wrangler.jsonc` | Toolchain pins, build-time token, bindings |

## Runtime/Tooling Preferences

- Android: JDK 17 toolchain; Gradle 8.9 via the committed wrapper; AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28; compile/target SDK 35, minSdk 26. Versions are inline in the Gradle files (no version catalog); module-level repositories are rejected by `FAIL_ON_PROJECT_REPOS`.
- Worker: Node ≥ 24 is required (native TypeScript execution and `node:sqlite`); npm with the committed lockfile. No Bun, pnpm, or yarn. Tests run offline and need no secrets.
- Secrets stay outside the repository: `~/.config/jagadaun/family-token` is read at Gradle configuration time (absent ⇒ empty token, clients fail closed) and is injected into `BuildConfig`; Worker secrets are `FAMILY_TOKEN` and `PLANTNET_API_KEY`. Never print, copy, or commit values.
- Ignored/generated: `.gradle/`, `.kotlin/`, `**/build/`, `local.properties`, `worker/node_modules/`, `worker/dist/`, `.env*`, `.dev.vars*`, keystores. Room schema JSONs are the deliberate exception.

## Testing & QA

- Android: JUnit 4 with `androidx.test` only — no Robolectric, MockK, or coverage plugin. JVM tests cover parsers, thresholds, scheduling, and UI state; instrumentation tests cover Room idempotency, photo promotion/rollback, and migration `1 → 2` using isolated UUID-named databases.
- Worker: `node:test` + `node:assert/strict` + `node:sqlite`. Provider calls are stubbed via `globalThis.fetch`, and the quota SQL runs against real in-memory SQLite, so quota behavior is executed rather than string-matched. Never let tests hit Pl@ntNet or GBIF.
- Test counts in the docs are point-in-time; run the suites instead of quoting numbers. There are no coverage thresholds.
- On a phone holding family data, avoid `connectedDebugAndroidTest`: it can uninstall the app and erase `databases/` and `files/photos`. Use the manual `assembleDebugAndroidTest` + `adb install -r` + `am instrument` path.
- Beta/release QA is manual on a physical phone: photo → analysis → save → reminder; airplane mode; denied permissions; missing photo; exhausted quota; 200% font. After an upgrade install, compare SQLite rows and photo hashes before/after.
- Known gaps: no Android tests for `NearbyClient`/`NearbyViewModel`/map projection, no Compose UI or navigation tests, and `worker/test/` is outside `tsconfig`, so it is not typechecked.
