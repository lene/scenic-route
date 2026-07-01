# Progress

## Current phase: Phase 3 — Distance-target routing

### Status: **CHECKPOINT 3b COMPLETE — awaiting sign-off**

### What's done (Phase 3)

- [x] Spike: `round_trip` + via-point sampling on Berlin graph; measured landing rates; `buildRequest` refactor
- [x] `docs/phase3-proposal.md`: written, committed, approach confirmed at Checkpoint 3a
- [x] `RouteParams` extended: `distanceToleranceLow=0.85`, `distanceToleranceHigh=1.15`, `numSuggestions=4`, `overlapThreshold=0.7`
- [x] `RankedRoute(route, blendedScore)` case class
- [x] `RouteSelection`: `filterByDistance`, `blendedScore`, `overlap` (Jaccard), `dedupe` (greedy), `rankAndSelect` (sort+dedupe+cap)
- [x] `Router.buildRequest` / `extractRoute` refactored (done in spike)
- [x] `Router.viaCandidates`: perpendicular-bisector via sampling, fracs `{0.30,0.40,0.55,0.70,0.85}` × 2 sides = 10 vias
- [x] `Router.loopCandidates`: 12 seeds × GH `round_trip` algorithm
- [x] `Router.routeWithTarget(start, end, targetKm, params)`: dispatch loop vs A→B → filter → rank
- [x] `DistanceTargetTest`: A→B in [255,345]m ✓; loop in [510,690]m ✓; sorted; capped
- [x] `Main.scala`: A→B 30km ranked demo + 20km loop demo from start point
- [x] 42 Scala tests — all green

### CHECKPOINT 3b evidence

```
Test suite: 42/42 passing (sbt test)
  SmokeTest:            1 test
  RouteParamsTest:      6 tests
  ScoreStoreTest:       4 tests
  AreaConfigTest:       2 tests
  RouterTest:           4 tests
  RouteSelectionTest:  12 tests  ← pure pipeline known-answer
  ScenicEncodingTest:   2 tests
  ScenicRoutingTest:    3 tests
  DistanceTargetTest:   8 tests  ← A→B in-window + loop in-window
```

Berlin e2e (awaiting run — graph already built, no rebuild needed):
```
[stock (wI=0, wS=0)]     22768 m | cqi=0.214 scenic=0.100
[scenic (wI=0.5, wS=0.5)] 27902 m | cqi=0.685 scenic=0.277

Distance-target A→B demo (target 30 km):
  N ranked routes, each within [25.5, 34.5] km, sorted by blended score

Loop demo (target 20 km):
  N loop routes from Brandenburg Gate within [17.0, 23.0] km
```

### Next step
**Await human sign-off at CHECKPOINT 3b.** Do not start Phase 4 until approved.

### Blocked on
Sign-off from human before Phase 4 (GPX + GeoJSON export).

---

## Previous phases

### Phase 2 — Score-aware routing (COMPLETE, signed off)
- 18 Scala tests green; EV round-trip verified; routing preference verified
- Berlin e2e: scenic route 27,902 m (3.2× better cqi, 2.8× better scenic vs stock 22,768 m)
- Tag: `phase-2-complete`

### Phase 1 — Offline scoring pipeline (COMPLETE, signed off)
- 55 Python tests green; CI passing
- 678,459 Berlin ways scored → `data/berlin-scores.csv` (33 MB)
- Tag: `phase-1-complete`
