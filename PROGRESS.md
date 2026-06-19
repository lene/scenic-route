# Progress

## Current phase: Phase 0 — Skeleton & vanilla routing

### Status: **CHECKPOINT 0 COMPLETE — awaiting sign-off**

### What's done
- [x] sbt skeleton (Scala 3.6.4, JDK 21, WartRemover 3.5.8, scalafix, scalafmt)
- [x] Strict compiler flags: -Werror -Wunused:all -Wvalue-discard -Yexplicit-nulls -language:strictEquality
- [x] CI: GitHub Actions scala job (scalafmt + scalafix + test) + python job (pytest)
- [x] LatLon, AreaConfig + TOML loader (tomlj, null-safe via .nn)
- [x] areas/berlin.toml (relation 62422, buffer 80km, Geofabrik URLs, demo coords)
- [x] Python clip-polygon generator (make_clip_polygon.py; EPSG:25833 buffer; 4 tests)
- [x] scripts/fetch.sh + scripts/clip.sh (manual data pipeline; reads from area TOML)
- [x] Makefile (data, build, test targets)
- [x] Route / RouteError domain types
- [x] Router: fromOsm + route(start, end) → Either[RouteError, Route] (LM mode, GH 11.0)
- [x] 7 Scala tests + 4 Python tests — all green

### CHECKPOINT 0 evidence
```
Area: berlin (boundary relation 62422, buffer 80 km)
Route: Brandenburg Gate (52.5163, 13.3777) → Müggelsee (52.4275, 13.6517)
Result: 22,768 m, 267 points
Graph build: ~69s first run; reload <1s
```

### Next step
**Await human sign-off at CHECKPOINT 0.** Do not start Phase 1 until approved.

### Blocked on
Sign-off from human before Phase 1.
