# Progress

## Current phase: Phase 2 — Score-aware routing

### Status: **CHECKPOINT 2 COMPLETE — awaiting sign-off**

### What's done (Phase 2)

- [x] `WayQuality(cqiQuality, scenicQuality)` + `ScoreStore.deriveSignals` — pure sub-blend formulas
- [x] `ScoreStore.load(path)` — CSV parser, graceful degradation on missing file (logs to stderr)
- [x] `ScenicTagParser` — writes derived EV values per way at GH import time
- [x] `ScenicImportRegistry` — wraps `DefaultImportRegistry`; returns `ImportUnit` for `cqi_quality` / `scenic_quality`
- [x] `RouteParams(infraWeight, scenicWeight, gradientWeight)` with `default = (0.5, 0.5, 0.0)`
- [x] `Router.fromOsm(osmFile, graphCache, scoreFile)` — loads scores, registers `ScenicImportRegistry` before `init()`
- [x] Per-request custom model: two successive MULTIPLY priority statements (multiplicative proxy for additive blend)
- [x] Path details (`cqi_quality`, `scenic_quality`) on `GHRequest`; `Route.meanCqiQuality` / `meanScenicQuality` (length-weighted)
- [x] `Main.scala` — before/after demo: stock (wI=wS=0) vs scenic (wI=wS=0.5), prints distance + mean EV scores
- [x] `ScenicEncodingTest` — EV round-trip: `cqi_quality`/`scenic_quality` path-detail means match derived values (±0.01)
- [x] `ScenicRoutingTest` — scenic weights pick the longer high-scoring detour; stock weights pick the short direct way
- [x] 18 Scala tests — all green

### CHECKPOINT 2 evidence

```
Test suite: 18/18 passing (sbt test)
  SmokeTest:          1 test
  RouteParamsTest:    2 tests
  ScoreStoreTest:     4 tests
  AreaConfigTest:     2 tests
  RouterTest:         4 tests
  ScenicEncodingTest: 2 tests  ← EV round-trip verified
  ScenicRoutingTest:  3 tests  ← routing preference verified

Key test assertions:
  EV round-trip (way 202: cqi=80, lts=1, green=0.9, blue=0.8):
    meanCqiQuality   = 0.86 ± 0.01  ✓
    meanScenicQuality= 0.85 ± 0.01  ✓
  Routing preference (parallel ways, same endpoints):
    stock (wI=wS=0): distanceMeters < 250 m  ✓ (takes direct ~222 m way)
    scenic (wI=wS=0.5): distanceMeters > 260 m  ✓ (takes scenic ~301 m detour)
    scenic route meanScenicQuality > direct route  ✓
```

### Sanity assessment
- Score store loads CSV and derives both EVs cleanly ✓
- Tag parsers attach to GH import registry; EVs round-trip at 0.01 resolution ✓
- Per-request weights change route selection without graph rebuild ✓
- `Main.scala` prints stock vs scenic comparison for the demo area ✓

### Next step
**Await human sign-off at CHECKPOINT 2.** Do not start Phase 3 until approved.

### Blocked on
Sign-off from human before Phase 3 (multi-candidate routing, distance targets, GPX export).

---

## Previous phases

### Phase 1 — Offline scoring pipeline (COMPLETE, signed off)

- 55 Python tests green; CI passing
- 678,459 Berlin ways scored → `data/berlin-scores.csv` (33 MB)
- Tag: `phase-1-complete`
