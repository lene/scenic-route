# Decisions

Chosen directions with one-line rationale. See JOURNAL.md for rejected paths.

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **Scala 3.6.4** (not 2.13 or 3.3.x) | Latest stable; WartRemover 3.5.8 supports it |
| 2 | **WartRemover 3.5.8** (not 3.2.5) | 3.2.5 has no artifact for Scala 3.6.4; 3.5.8 does |
| 3 | **Strict compiler flags** compensate for partial Scala 3 WartRemover | `-Werror -Wunused:all -Wvalue-discard -Yexplicit-nulls -language:strictEquality` fill gaps where WartRemover Scala 3 support is a subset |
| 4 | **GraphHopper embedded** (`graphhopper-core` + `graphhopper-web-api` 11.0) | JVM-native, millisecond queries, supports LM/CH modes; GHRequest/GHResponse live in web-api artifact (not core) |
| 5 | **LM (hybrid/landmark) mode** for Phase 0–3 | Weights tunable per-request; CH bakes weights at build time and is incompatible with per-request custom models |
| 6 | **Custom model with speed cap 15 km/h** (not `fastest` weighting) | GH 11.0 dropped the `fastest` weighting; custom model is now required; Phase 2 will extend it with scenic_quality and gradient terms |
| 7 | **tomlj** (Apache-2.0) for TOML parsing in Scala | Lightweight, no transitive deps, easy Java interop; stdlib Python `tomllib` reads the same files |
| 8 | **EPSG:25833** (ETRS89/UTM zone 33N) for clip-polygon buffering | Metric CRS appropriate for central Europe; ~0.1% area distortion at Berlin's latitude |
| 9 | **`pipeline/`** directory for Python scoring code | Keeps Python and Scala source trees separate; `conftest.py` adds it to sys.path for pytest from repo root |
| 10 | **Explicit `.nn` + `.longValue()`/`.doubleValue()`** for Java interop | Required by `-Yexplicit-nulls` (no implicit null widening) and WartRemover AutoUnboxing wart |
| 11 | **Explicit `Left[A,B]`/`Right[A,B]` type params** in Router | WartRemover Nothing wart fires when Scala infers `Left[RouteError, Nothing]`; explicit types suppress it cleanly |
