# QuakeAlert – Test coverage and how to add more

## Is the current coverage enough?

**For the critical alert path: yes.** The logic that decides “DANGER vs silent” and “open red warning screen” is covered: distance, radius, earthquake tag, geo parsing, and quake report parsing. Regressions there will be caught.

**For the whole app: no.** UI, services, DB, workers, and full flows are untested. The current suite is a **good baseline** to extend.

---

## Test layout (data context)

| Test class | Path | Production code under test | Count |
|------------|------|----------------------------|-------|
| **AlertLogicTest** | `app/src/test/.../util/AlertLogicTest.kt` | `util/Util.kt`: `splitTags`, `hasEarthquakeTag`, `quakeDisplayPriority` | 11 tests |
| **GeoUtilTest** | `app/src/test/.../util/GeoUtilTest.kt` | `util/GeoUtil.kt`: `distanceKm`, `extractGeoCoordinates`, `formatDistanceKm` | 10 tests |
| **QuakeReportParserTest** | `app/src/test/.../db/QuakeReportParserTest.kt` | `db/QuakeReportParser.kt`: `parseReports`, `parseQuakeTime` | 9 tests |

**Total: 30 unit tests.** All run on the JVM (no device/emulator). Run with:

```bash
./gradlew testFdroidDebugUnitTest   # no google-services.json needed
./gradlew testPlayDebugUnitTest     # needs google-services.json
```

---

## What each test file covers (detail)

### 1. AlertLogicTest

- **splitTags** – null/empty → `[]`, single tag, comma-separated, with spaces (no trim on parts).
- **hasEarthquakeTag** – `"earthquake"` (any case), with other tags, with spaces; no match for empty/wrong tag.
- **quakeDisplayPriority** – null distance → notification priority; within radius → `PRIORITY_MAX`; outside → `PRIORITY_MIN`; exactly on radius → max.

**Production:** `id.my.bananapixel.quakealert.util.Util` (top-level functions). Used by `msg/NotificationService.kt` when building notifications.

### 2. GeoUtilTest

- **distanceKm** – same point → 0; two points ~1° apart in range 100–200 km; 1° at equator ≈ 111 km.
- **extractGeoCoordinates** – `geo:lat;lon` alone or with other tags; spaces; no geo → null; wrong separator / non-numeric → null.
- **formatDistanceKm** – one decimal (e.g. 12.34 → `"12.3"`).

**Production:** `id.my.bananapixel.quakealert.util.GeoUtil`. Used by `NotificationService` for distance and notification title.

### 3. QuakeReportParserTest

- **parseReports** – `[]` → empty list; single/multiple items; missing fields (defaults/NaN); empty string / invalid JSON / root `{}` → empty list.
- **parseQuakeTime** – valid UTC string → epoch in expected range; `1970-01-01 00:00:00` → 0L; null/empty/wrong format → current time.

**Production:** `id.my.bananapixel.quakealert.db.QuakeReportParser`. Used by `db/QuakeRemoteMediator.kt` and (if present) `Repository` quake fetch.

---

## Patterns used (so you can add more)

1. **Extract testable functions** – Logic that depends on `Context`/`Repository` stays in the service/ViewModel; pure logic (inputs → output) is moved to `Util`, `GeoUtil`, or a parser object and tested in `src/test`.
2. **Same package in test** – Test package mirrors production, e.g. `...util.AlertLogicTest` for `...util.Util`. No need to use `internal` for test visibility if you keep the same module.
3. **JUnit 4** – `@Test`, `assertEquals`, `assertTrue`, `assertNull`. Dependencies: `junit:junit:4.13.2`, `kotlin-test-junit`, `org.json:json` (for parser tests on JVM).
4. **No Android framework in unit tests** – Tests run on JVM. Don’t use `Context`, `Activity`, or Android SDK in these tests; use mocks or move to `androidTest` if you need them.
5. **Parsers / JSON** – `QuakeReportParser` returns a list or empty list and catches `JSONException`; tests use `org.json` (same API as Android’s stub). For other APIs, same idea: extract a small “parse(this string) → this model” function and test it.

---

## Gaps and where to add tests (future)

Use this as a checklist when you want to add tests. Paths are relative to `app/src/main/java/id/my/bananapixel/quakealert/` unless noted.

| Area | Production code (candidates) | Suggested test approach |
|------|-------------------------------|--------------------------|
| **Notification building** | `msg/NotificationService.kt` – channels, intents, insistent | Extract “title for quake” / “channel id” helpers; test with mock Repository or fake data. |
| **Repository quake fetch** | `db/Repository.kt` – `fetchQuakes`, `executeFetchReports`, `parseReports` | If it duplicates parser logic, call `QuakeReportParser` and rely on parser tests; else add a small test that same JSON → same list. |
| **Distance + priority + tag together** | `NotificationService.displayInternal` (uses Util + GeoUtil) | Optional: one test that given (tags, userLat, userLon, radius) → expected priority and “is danger”. Use only extracted functions + a tiny helper. |
| **Quake time in Mediator** | `db/QuakeRemoteMediator.kt` – uses `QuakeReportParser.parseQuakeTime` | Already covered via parser tests. Add Mediator test only if you add more logic (e.g. page calculation). |
| **DB / Room** | `db/Database.kt`, `db/Repository.kt`, DAOs | Use in-memory Room in `androidTest` or a test DB; test migrations or critical queries. |
| **ViewModels** | `ui/QuakeViewModel.kt`, `ui/MainViewModel.kt`, etc. | Test with fake Repository (interface) and `runTest`; assert flows and state. |
| **Fragments / UI** | All `ui/*Fragment.kt`, Activities | Instrumented tests (`androidTest`) with FragmentScenario or Espresso; or UI tests with Compose/View. |
| **Workers** | `work/PollWorker.kt`, `DeleteWorker.kt`, `LocationWorker.kt` | Use WorkManager testing APIs (e.g. `WorkManagerTestInitHelper`) in `androidTest`. |
| **Services** | `service/SubscriberService.kt`, WebSocket, etc. | Integration-style tests with test server or mocks; or focus on small, extracted “parse this message” functions. |
| **Settings / prefs** | `ui/SettingsActivity.kt`, `Repository` prefs | Test Repository get/set with a test Context or in-memory prefs; keep UI in instrumented tests. |

---

## Quick reference: key production files

- **Alert / notification logic:** `util/Util.kt` (splitTags, hasEarthquakeTag, quakeDisplayPriority), `util/GeoUtil.kt`, `msg/NotificationService.kt`
- **Quake API / history:** `db/QuakeReportParser.kt`, `db/QuakeRemoteMediator.kt`, `db/Repository.kt` (quake fetch), `ui/QuakeViewModel.kt`, `ui/HistoryFragment.kt`
- **Warning screen:** `app/AlertState.kt`, `ui/WarningFragment.kt`
- **Push / subscriptions:** `service/SubscriberService.kt`, `msg/NotificationDispatcher.kt`, Firebase/UnifiedPush in `firebase/`, `up/`

---

## Summary

- **Enough for the critical path:** Yes – alert priority, earthquake tag, distance, geo, and quake parsing are covered.
- **Enough for the whole app:** No – use this doc to add tests for Repository, ViewModels, UI, workers, and services when you need them.
- **How to add more:** Extract small, pure functions or parsers; put unit tests in `app/src/test/...` mirroring package; use the “Gaps” table and “Patterns” section above.
