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

**osmium .poly format**: two `END` lines required (first closes the ring, second closes the polygon definition). One `END` causes "Expected 'END' for end of (multi)polygon" at osmium extract time.

**osmium extract takes one input file**: `osmium extract` does not accept multiple positional PBF inputs. Must `osmium merge` Berlin + Brandenburg first, then extract from the merged file.

**bash heredoc + shell variables**: `<<'DELIMITER'` (single-quoted) prevents all variable expansion inside the heredoc. Used env vars (`GEOJSON_PATH=... python3 - <<'PYEOF'`) to pass shell variables into a single-quoted heredoc.

---

## 2026-06-20 Phase 1 scoring pipeline

**CI: sbt not pre-installed on ubuntu-latest**: `actions/setup-java` with `cache: sbt` caches the sbt artifact cache but does NOT install sbt itself. Newer ubuntu-latest images dropped the pre-installed sbt. Fix: add `uses: sbt/setup-sbt@v1` step before any `sbt` command.

**pyrosm returns GeoDataFrames cleanly**: `osm.get_network(network_type="cycling")` returns ways with `id` column (int64) usable as OSM way id. `get_data_by_custom_criteria` with `custom_filter` returns matching features. Both work against the 247MB Berlin clip. Load time ~150s.

**Full scoring build runtime**: 678,459 ways × (sidepath STRtree + per-way buffer + scenic overlap + blend) took ~73 min. The bottleneck is the Python loop + `union_all` of nearby green/blue features per way. Acceptable for a rare manual job; if this becomes painful, vectorise scenic overlap via `geopandas.sjoin` (aggregate intersection area by way_id across all feature pairs) — avoids per-way `union_all`.

**Sidepath names missing from pyrosm cycling network**: Ways like "Landwehrkanal towpath" and "Grunewald" paths don't appear with those names in the cycling network because OSM ways along canals/forests often carry no name tag (or a different name). Not a scoring problem — the scoring uses geometry/tags, not names. Spot-checks found representative examples via tag/class filtering instead.

**Karl-Marx-Allee green=1.0 at cqi=12.8**: A primary road scoring full green is initially surprising, but Karl-Marx-Allee is heavily tree-lined and the 30m buffer corridor picks up the adjacent Volkspark Friedrichshain. Score still 0.295 (pulled down by low CQI). This is a reasonable outcome — the road is scenic but hostile to cycling, which is exactly what the blend captures.

**Havelchaussee tagged as tertiary not cycleway**: The Havelchaussee is a road (tertiary) through Grunewald forest, not a dedicated cycling way — hence cqi=45, lts=2 and high green (0.927). Correct: it's a scenic but shared road. Phase 2 routing will prefer it for scenic value while penalising the traffic-mix cost.

**score_file path**: Added to berlin.toml under `[paths]`; Scala AreaConfig does not read it in Phase 1 (Phase 2 adds that).
