# Journal

Append-only. Discoveries, dead ends, gotchas, surprises. Distinct from DECISIONS.md (which records chosen directions) — this records what was *learned*, including negative results.

---

## 2026-06-18 Phase 0 bootstrap

**WartRemover + Scala 3.6.4**: `sbt-wartremover 3.2.5` has no compiler plugin for Scala 3.6.4 (`wartremover_3.6.4:3.2.5` not on Maven Central). Must use 3.5.8 which matches the `wartremover_3.6.4:3.5.8` artifact. When upgrading WartRemover always verify `wartremover_<scalaVersion>:<wartVersion>` exists before pinning.

**GH 11.0: `fastest` weighting removed**: `Profile("bike").setWeighting("fastest")` throws at `importOrLoad` — GH 11.0 dropped the `fastest` weighting entirely. Must use `setCustomModel(CustomModel().addToSpeed(Statement.If("true", Op.LIMIT, "15")))`. Phase 2 will extend the custom model with `scenic_quality` and gradient.

**GH 11.0: config must be populated before `init()`**: `gh.setGraphHopperLocation()` and `gh.setOSMFile()` called after `init()` are silently ignored or cause "no graph.location provided" errors. Use `GraphHopperConfig.putObject("graph.location", ...)` and `putObject("datareader.file", ...)` BEFORE passing config to `init()`.

**GH 11.0: `import.osm.ignored_highways` required**: Failing to set this parameter throws `IllegalArgumentException` at `init()`. Default sensible value for bike routing: `List.of("motorway", "trunk")`.

**GH 11.0: `road_access` encoded value**: Using `road_access` in a custom model priority statement requires `graph.encoded_values: road_access` in the config, or GH throws at `prepareImport`. For Phase 0, removed the `road_access` priority statement; will re-add in Phase 2 when encoded values are being configured for `scenic_quality` anyway.

**GH 11.0: `graphhopper-web-api` is a separate artifact**: `GHRequest`, `GHResponse`, `GHPoint`, `ResponsePath`, `PointList`, `CustomModel`, `Statement` are in `com.graphhopper:graphhopper-web-api:11.0`, NOT in `graphhopper-core`. Both must be on the classpath.

**WartRemover `Nothing` wart on `Either`**: `Left(x)` infers `Left[X, Nothing]` which triggers the `Nothing` wart. Fix: explicit type params `Left[RouteError, Route](x)`.

**WartRemover `ToString` wart**: `.toString` on types that don't explicitly override `toString` is blocked. Added `def errorMessage: String` to `RouteError` enum and use that instead of `.toString` in error messages.

**WartRemover `AutoUnboxing` wart + `-Yexplicit-nulls`**: Java interop methods returning `java.lang.Long`/`java.lang.Double` trigger both the AutoUnboxing wart (implicit unbox) and the null-check requirement. Pattern: `.nn.longValue()` / `.nn.doubleValue()`.

**Python path for pytest**: When running `python -m pytest pipeline/tests/` from repo root, the `pipeline/` dir is not on `sys.path`. Fixed with `tests/conftest.py` that `sys.path.insert(0, pipeline_parent)`.
