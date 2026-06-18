# Progress

## Current phase: Phase 0 — Skeleton & vanilla routing

### Status: implementation complete; awaiting CHECKPOINT 0 manual demo

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

### Next step
**CHECKPOINT 0 (manual demo)**:
1. Install osmium-tool: `pkexec apt install osmium-tool`
2. `make data` (fetch Berlin + Brandenburg, clip to 80km polygon)
3. `sbt run` or write a small Main invocation that routes Brandenburg Gate → Müggelsee
4. Capture output (distance + point count) as acceptance evidence
5. Post evidence here and await sign-off before starting Phase 1

### Blocked on
Nothing blocking — data pipeline scripts are untested on real data; the fiddly
step is extracting boundary relation 62422 from the Berlin PBF.
