# Progress

## Current phase: Phase 1 — Offline scoring pipeline

### Status: **CHECKPOINT 1 COMPLETE — awaiting sign-off**

### What's done (Phase 1)
- [x] Pure-Python GeoPandas + shapely STRtree stack (no PostGIS) — DECISIONS #12
- [x] CQI tag-class scoring: highway base, cycle infra lift, surface factor, maxspeed factor (tags.py)
- [x] LTS 1–4 mapping from tags (tags.py)
- [x] Score blend with zero-weight gradient seam (blend.py) — DEFAULT_WEIGHTS: cqi=0.35, lts=0.15, green=0.25, blue=0.25, gradient=0
- [x] Green/blue feature predicates (features.py) — tag sets per SPEC §5.2
- [x] Buffer-overlap scenic scoring in EPSG:25833 (scenic.py)
- [x] Idempotent CSV way-ID store (store.py) — global, area-independent, skip already-scored ways
- [x] Sidepath detection via STRtree + ≥2/3 check-point test (sidepath.py)
- [x] CQI with parallel-road inheritance (cqi.py) — sidepath gets environment factor from road speed
- [x] PBF loading via pyrosm (io_osm.py) — lazy import so pure-logic tests need no geo stack
- [x] Pipeline orchestration (pipeline.py) — project EPSG:25833, sidepath resolve, CQI, scenic, blend, store
- [x] CLI: score_area.py <area.toml>; Makefile `score` target
- [x] areas/berlin.toml: added score_file = "data/berlin-scores.csv"
- [x] 55 Python tests — all green; CI: both scala + python jobs green
- [x] Full Berlin build: 678,459 ways scored in ~73 min → data/berlin-scores.csv (33 MB)

### CHECKPOINT 1 evidence

```
Score table: data/berlin-scores.csv
Ways scored: 678,459
Schema: way_id, cqi (0..100), lts (1..4), green (0..1), blue (0..1), score (0..1)

Named spot-checks:
  Volkspark path          way 57922502   cqi=80.0  lts=1  green=0.781  blue=0.022  score=0.631
  Berliner Allee primary  way 5051956    cqi=12.8  lts=4  green=0.000  blue=0.000  score=0.045
  Karl-Marx-Allee prim.   way 4615614    cqi=12.8  lts=4  green=1.000  blue=0.000  score=0.295
  Havelchaussee tertiary  way 4422232    cqi=45.0  lts=2  green=0.927  blue=0.000  score=0.489
  Schöneberg res/30       way 60825560   cqi=60.0  lts=1  green=0.057  blue=0.155  score=0.413
  Cycleway/asphalt        way 4429742    cqi=90.0  lts=1  green=0.537  blue=0.000  score=0.599

Top canal-adjacent quiet ways: blue=1.0, cqi~60, score~0.61
Best paths (green+blue=1.0, cqi=80): score=0.930
Worst (trunk, no scenery): cqi=2, lts=4, score=0.007

Distribution (n=678,459):
  score: mean=0.364  median=0.342  stdev=0.124
  cqi:   mean=52.6   median=55.0
  67% of ways have green > 0
```

### Sanity assessment
- Busy primaries (Berliner Allee): score=0.045 ✓ correctly low
- Volkspark path: green=0.78, score=0.63 ✓ scenic path rewarded
- Canal-adjacent quiet way: blue=1.0, score=0.61 ✓ water proximity captured
- Best paths (forest+lake edge): score=0.930 ✓ top of range
- Trunk/no-scenery: score=0.007 ✓ bottom of range

### Next step
**Await human sign-off at CHECKPOINT 1.** Do not start Phase 2 until approved.

### Blocked on
Sign-off from human before Phase 2 (score-aware GraphHopper routing).
