# Progress

## Current phase: Phase 4 — Output (GPX + GeoJSON)

### Status: **CHECKPOINT 4 COMPLETE — awaiting sign-off**

### What's done (Phase 4)

- [x] `RouteExport.toGeoJson`: FeatureCollection, LineString `[lon,lat]`, rank/score
      properties + simplestyle `stroke`; `Locale.ROOT` formatting
- [x] `RouteExport.toGpx`: one `<gpx 1.1>` with N `<trk>`, XML-escaped name, no `<ele>`
- [x] `Main.parseLatLon` / `parseArgs`: pure `Option`-returning CLI parsers, range-checked
- [x] `Main` wired: CLI `<areaToml> <lat,lon> <lat,lon> <targetKm>` → routes → writes
      `out/<area>/<mode>-<km>km.{geojson,gpx}`; no-arg falls back to the demo (also writes)
- [x] `/out/` git-ignored; `docs/usage.md` added
- [x] 60 Scala tests — all green

### CHECKPOINT 4 evidence

```
Test suite: 60/60 passing (sbt test)
  + RouteExportTest:  10 tests  ← GeoJSON/GPX known-answer incl. German-locale regression
  + MainCliTest:       8 tests  ← parseLatLon / parseArgs
  (existing 42 unchanged)
```

Berlin e2e (verified 2026-07-01, real graph, CLI-arg path):
```
$ runMain scenicroute.Main areas/berlin.toml 52.5163,13.3777 52.4275,13.6517 30
  a2b ranked routes (target 30.0 km): 4 routes → Wrote out/berlin/a2b-30km.{geojson,gpx}
$ runMain scenicroute.Main areas/berlin.toml 52.5163,13.3777 52.5163,13.3777 20
  loop ranked routes (target 20.0 km): 4 routes → Wrote out/berlin/loop-20km.{geojson,gpx}

Well-formedness (python json.load + xml.dom.minidom):
  a2b-30km.geojson  — 4 features, first coord [13.377705, 52.51627] (lon,lat), 69.5 KB
  loop-20km.geojson — 4 features, 48.4 KB
  a2b-30km.gpx      — 4 trk, 3119 trkpt, dot decimals, 147 KB
  loop-20km.gpx     — 4 trk, 2162 trkpt, 102 KB
  ALL FILES WELL-FORMED
```

### Next step
**Await human sign-off at CHECKPOINT 4.** This is the last v1 phase — after sign-off
the SPEC §10 definition-of-done is met (ranked scenic routes near target, exportable
as GPX + GeoJSON, tunable per request).

### Blocked on
Sign-off from human to close Phase 4 / v1.

---

## Previous phases

### Phase 3 — Distance-target routing (COMPLETE, signed off)
- 42 Scala tests green; via-sampling A→B + native round_trip loops → filter/rank/dedupe
- Berlin e2e: 4 A→B routes in [25.5,34.5] km, 4 loops in [17.0,23.0] km, score-ranked
- Tag: `phase-3-complete`

### Phase 2 — Score-aware routing (COMPLETE, signed off)
- 18 Scala tests green; EV round-trip verified; routing preference verified
- Berlin e2e: scenic route 27,902 m (3.2× better cqi, 2.8× better scenic vs stock 22,768 m)
- Tag: `phase-2-complete`

### Phase 1 — Offline scoring pipeline (COMPLETE, signed off)
- 55 Python tests green; CI passing
- 678,459 Berlin ways scored → `data/berlin-scores.csv` (33 MB)
- Tag: `phase-1-complete`
