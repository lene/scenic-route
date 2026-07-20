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

---

## 2026-07-01 Phase 2 — score-aware routing

**GH 11.0 value expression limitation**: Each priority statement's value expression may only reference a single encoded value. Attempted `"0.2 + 0.8 * (wI * cqi_quality + wS * scenic_quality)"` — throws at routing time with a parse/validation error. Workaround: two successive `If("true", MULTIPLY, "0.2 + wX * ev_x")` statements. This is a multiplicative proxy for the additive blend (DECISIONS #19).

**`ReaderWay.getId()` is a primitive `long`**: Unlike most Java interop in GH that returns boxed types, `ReaderWay.getId()` returns `long` directly. No `.nn` call needed — the Scala 3 null-safety layer only applies to reference types. Calling `.nn` on a primitive causes a compile error.

**WartRemover `Any` wart on `System.err.println`**: `println(msg)` widens `String` to `Any` via Scala's `Predef.println` signature. Suppressed with `@SuppressWarnings(Array("org.wartremover.warts.Any"))` scoped to the `load` method only — not the whole class.

**WartRemover `Any` wart in test `fail()` with string interpolation**: `fail(s"msg: ${expr}")` widens the interpolated value to `Any`. Fix: pass the string directly without interpolation — `fail(e.errorMessage)` — matching the pattern in RouterTest.

**Path details API for mean EV on a route**: `GHRequest.setPathDetails(util.List.of("cqi_quality","scenic_quality"))` registers detail collectors; `path.getPathDetails().get("ev_name")` returns `List[PathDetail]` where `PathDetail.getValue()` is `Object|Null` (always `java.lang.Double` for decimal EVs) and `getLength()` is the edge point count. Length-weighted mean gives the correct segment-proportional average.

**`gh.setImportRegistry()` ordering confirmed critical**: Calling after `init()` silently skips registration; graph imports with all EV values at default 0. Must be called before `init()` (DECISIONS #20, also noted in Phase 0 JOURNAL but easy to forget).

**Scenic routing test geometry**: With two parallel cycleways — direct (~222 m, low scores) and detour (~301 m, high scores) — `RouteParams(0.5, 0.5, 0.0)` produces priority ~0.394 vs ~0.06 for the direct way. The scenic detour wins by ~5x cost advantage despite being 36% longer by distance. `RouteParams(0, 0, 0)` gives uniform priority so the shorter way wins.

---

## 2026-07-01 Phase 3 spike — distance-target routing

**GH `round_trip` landing rate**: 10 seeds × 3 targets (15, 20, 30 km) on Berlin graph → **100% in [0.85N, 1.15N]** window every time. GH's built-in round_trip reliably hits target length. Distinct seeds yield distinct topologies. Single-point request only (start lat/lon); use `Parameters.Algorithms.ROUND_TRIP` + hints `RoundTrip.DISTANCE`, `RoundTrip.SEED`, `RoundTrip.POINTS`. Custom model (priority blend) applies normally.

**Via-point road-distance overhead**: Routing `start → via → end` where via is placed at perpendicular bisector offset `h = sqrt((T/2)² - (D/2)²)` (fraction f of hMax): road distance is 30–60% longer than the straight-line via triangle. f=0.40–0.55 landed for T=30 km; f=0.70–0.85 for T=40 km. Landing rate ~50% from 10 vias per target; expect 6–8 from 16 vias. Sufficient for top-4 output.

**Via-point upper fractions overshoot**: f=1.0 (hMax) consistently overshoots by 20–50%. Cap fracs at 0.85 in production; using {0.30, 0.40, 0.55, 0.70, 0.85} × both sides = 10 vias.

**WartRemover `Equals` wart on `== 0.0`**: Comparing `Double == 0.0` fires the Equals wart. Use `<= 0.0` (comparison operator, not equality) for the "no detour needed" case.

**WartRemover `Null` wart on `getBest() == null`**: `-Yexplicit-nulls` rejects `== null`; WartRemover rejects `eq null`. Use `Option(rsp.getBest()).map(_.nn.getDistance())` — wraps the nullable return in Option, calls `.nn` on the non-null path inside map.

**Router.buildRequest refactor (done early)**: Extracted per-request custom-model + path-details wiring into `private def buildRequest(points: List[LatLon], params)`. `route()`, `routeLoop()`, and `routeVia()` all delegate to it. This is Milestone B Task 4 done as part of the spike; kept as it simplifies the Router and all tests stay green (18/18).

**WartRemover `IterableOps` wart bans `head`/`last`**: Both trigger `IterableOps` wart. Use `headOption.getOrElse(...)` or pattern match on `List`. Also bans `tail`/`init`.

**WartRemover `DefaultArguments` wart**: Fires on any method with default parameter values. Test helper methods need all params passed explicitly (or use `@SuppressWarnings`).

**WartRemover `ListAppend` wart bans `:+`**: `List.:+` is O(n); wart rejects it. Use prepend `candidate :: acc` + `.reverse` at the end for O(n) total instead.

**WartRemover `AsInstanceOf` in PathDetail stub**: Subclassing `PathDetail` in tests and returning `Double.box(d)` (not `d.asInstanceOf[AnyRef]`) avoids the `AsInstanceOf` wart cleanly, since `Double.box` is an explicit boxing call.

**Loop (start==end) detection**: WartRemover `Equals` wart bans `==` on `Double`. Use `(a.lat - b.lat).abs < 1e-9 && (a.lon - b.lon).abs < 1e-9` for coordinate equality check.

**Integration test fixture reuse**: The `parallel.osm.xml` fixture (3 nodes, 3 ways — triangle graph) is sufficient for both A→B target-distance tests (via point snaps to node 12) and loop tests (GH round_trip traverses the triangle). No separate grid fixture needed.

**`String` interpolation in `assert` messages**: `assert(cond, s"... ${expr}")` widens `expr` to `Any`, triggering the wart. Use a plain string literal as the message instead.

---

## 2026-07-01 Phase 4 — output (GPX + GeoJSON)

**Locale landmine (the one that would silently ship broken files)**: the dev JVM default locale is German, so `String.format("%.6f", x)` / `f"$x%.6f"` emit `13,377` (comma) → invalid JSON and GPX. Fix: every numeric format goes through `String.format(Locale.ROOT, spec, Double.box(x))`. Guarded by a regression test that sets `Locale.setDefault(Locale.GERMANY)` (save/restore in `finally`) and asserts `.` decimals survive. Note the existing `f"..."` console prints in `Main` are *not* fixed — they're throwaway stdout, not files.

**WartRemover `Any` on every string interpolation**: `StringContext.s` has signature `s(args: Any*)`, so *any* `s"...$x..."` (even interpolating a `String`) infers `Any` and trips the wart at the char after each `${...}`. That's why `Main`, `Router.route`, `ScoreStore.load` all carry an object/method-level `Any` suppression. A serialisation module is all interpolation → suppress `Any` once at the object level.

**WartRemover `SeqApply` bans index access**: `Vector(i)` / `Seq(i)` are disabled (can throw `IndexOutOfBounds`). Use `.lift(i).getOrElse(default)` for a total lookup — used for the per-rank colour palette.

**GeoJSON coordinate order is `[lon, lat]`** (RFC 7946), but `LatLon` is `(lat, lon)` — emit `p.lon` then `p.lat`. Verified: first Berlin coordinate serialises as `[13.377705, 52.51627]`. GPX `<trkpt>` uses named `lat`/`lon` attributes so order there is irrelevant.

**`s.split(',')` (Char arg) is Scala, non-null; `s.split(",")` (String arg) is Java, nullable**: under `-Yexplicit-nulls` the Java `String.split(String)` returns `Array[String | Null] | Null`. The `StringOps.split(Char)` overload returns a plain `Array[String]`. Prefer the Char overload to avoid `.nn` noise. `String.trim()` is still Java-nullable, so `latS.trim.nn.toDoubleOption` needs the `.nn` before the `StringOps` extension applies.

**`Paths.get` / `Files.writeString` / `Path.resolve` usable without `.nn`**: their Java returns come back as flexible (unchecked-null) types, so they pass straight into `Path`-typed positions and interpolation. Only `Files.writeString`'s returned `Path` needs discarding — bind it to `val _ =` for `-Wvalue-discard`.

**e2e file shape (real Berlin graph)**: 4 tracks per file; A→B 30 km GPX ≈ 3119 `<trkpt>` / 147 KB, loop 20 km ≈ 2162 / 102 KB. Well-formedness confirmed by parsing GeoJSON with `json.load` and GPX with `xml.dom.minidom` — cheap, no new JVM test deps.

---

## 2026-07-02 V2 Milestone A — backend API (http4s + tapir + circe)

**Stack pins for Scala 3.6.4**: tapir 1.11.10, http4s 0.23.28 (ember), circe 0.14.10 resolve cleanly together (cats-effect 3). tapir-json-circe supplies `Schema[io.circe.Json]`, so a `RouteResp` carrying a raw `Json` GeoJSON field derives its tapir Schema via `generic.auto` without hand-writing one.

**circe semiauto derivation is WartRemover-clean**: `deriveDecoder`/`deriveEncoder` in companion objects produced zero warts. The friction is elsewhere (below).

**WartRemover `Any` on tapir/http4s builders**: tapir's endpoint capabilities type parameter is `Any` (no streaming), and http4s `Request/withEntity/uri` builders surface `Any` in inferred types. Both trip `Wart.Any`. Suppress once at the object/class level (`Routes`, `Server`, `RoutesTest`) — the endpoints/handlers are otherwise fully typed. Same pattern as `Main`/`RouteExport` for interpolation.

**scalafix `NoValInForComprehension` bans `x = expr` inside a for**: cats-effect `for { cfg = load(); ... }` value bindings are rejected. Wrap synchronous heavy work in `IO.blocking(...)` and bind with `<-`; build non-effect helpers (e.g. `routeFn`) as `val`s inside the `.use { ... }` block, not as for-bindings.

**`useForever` is `IO[Nothing]` → `Wart.Nothing`**: binding `_ <- server...useForever` infers Nothing. Append `.void` to get `IO[Unit]`. (Same family as the `Left(x)`/`Vector.empty` Nothing warts — annotate the value's type.)

**HTTP layer testable without a graph**: injecting routing as `RouteFn = (LatLon,LatLon,Double,RouteParams) => Seq[RankedRoute]` and geocoding as `GeocodeFn = String => IO[List[GeoResult]]` lets `RoutesTest` drive the real tapir/http4s stack in-memory (`app.run(request)`) with stubs — no GraphHopper, no network. `Server` is the only untested piece (composition root), verified by a live boot + curl.

**Nominatim**: returns `lat`/`lon` as JSON **strings** (not numbers) → parse via `.toDoubleOption`. Live call needs a real `User-Agent` (usage policy); wrap the client call in `.handleError(_ => Nil)` so a geocoder hiccup degrades to "no matches" rather than a 500.

## 2026-07-19 — V2 Milestone B (Frontend MVP)

**jsdom + `@testing-library/jest-dom`**: importing the bare `@testing-library/jest-dom` in the vitest setup throws `expect is not defined` (it calls `expect.extend` at import against a global that vitest doesn't expose unless `globals:true`). Use the vitest entrypoint `@testing-library/jest-dom/vitest` instead — it wires the matchers into vitest's own `expect`.

**jsdom `Blob` has no `.text()`/`.arrayBuffer()`**: asserting GPX content via `await blob.text()` fails with `b.text is not a function`. Assert `blob.type` + `blob.size` (which jsdom does implement) instead; content correctness of the GPX itself is already covered by the Scala `RouteExportTest`.

**MapLibre needs WebGL → can't render in jsdom**: `MapView` can't be unit-tested (no canvas/WebGL). Kept the map logic thin and pushed all testable logic into pure modules (`params`/`api`/`gpx`) + a `Sidebar` component test (the `canFind` branch). The map round-trip is the human's checkpoint acceptance.

**Marker `dragend` handler leak**: a `syncMarker` that re-attaches `marker.on('dragend', …)` on every render accumulates handlers → one drag fires N callbacks. Bind the handler **once at marker creation** and keep the drag callbacks stable (`useCallback([])` in `App`) so a persisting marker never needs re-binding.

**Once-bound map `click` vs. fresh state**: MapLibre's `map.on('click')` is bound once at init, so a captured React handler goes stale. Store the latest callback in a ref (`clickCb.current = props.onMapClick` each render) and call `clickCb.current(...)` from the bound listener.

**`import.meta.env` under strict `tsc`**: needs `/// <reference types="vite/client" />` (added as `src/vite-env.d.ts`) or `tsc -b` errors on `VITE_API_BASE`.

**`@types/geojson` is transitive via `maplibre-gl`**: the global `GeoJSON` namespace (e.g. `GeoJSON.FeatureCollection`) is already available — no need to add the dep. Our own minimal `GeoJson` wire type is cast to it at the MapLibre `setData` boundary.

**Bundle size**: MapLibre pushes the JS bundle to ~956 KB (267 KB gzip); Vite warns >500 KB. Acceptable for a personal tool over LAN. `// ponytail:` code-split MapLibre via dynamic `import()` only if first-load latency bites.

**Param mapping is where the two validators must agree**: `toParamsDto` is built so its output *always* passes `Api.toParams` (low = 1−p with p capped at 0.9 so low ∈ [0.1,1]; high = 1+p ≥ 1; low ≤ high; suggestions rounded+clamped to [1,5]). The frontend test asserts the band invariants directly so drift from the backend rules fails a unit test, not a live request.

## 2026-07-20 — Out-and-back (doubled-segment) penalty

**The router padded distance with out-and-backs**: to hit a target distance the loop/via generators would ride a road out and straight back. Fixed with a `doubledFraction(route)` self-overlap metric folded softly into `blendedScore`.

**`Route` has no edge/way IDs → doubling must come from `points`**: the existing between-route `overlap` is a Jaccard over a *set* of 1e-3°-rounded points — direction-agnostic and it collapses duplicates, so it is structurally blind to a single route doubling back. The self-overlap metric instead builds a *multiset* of consecutive-point segments, keyed on a finer ~1e-5° (~1 m) grid and direction-normalised (canonical endpoint order), each weighted by length; a key seen ≥2× is doubled. `doubled/total` ∈ [0,1]: 0 clean loop, 1 pure out-and-back.

**Reused GH `DistanceCalcEarth` for segment length** — no haversine helper exists in the repo, and a hand-rolled trig block is wart-bait. `com.graphhopper.util.DistanceCalcEarth` is already on the classpath (graphhopper-core); one private instance, `.calcDist(lat1,lon1,lat2,lon2)`.

**`Wart.Equals` bans `==` even on primitive `Long`**: direction-normalising the segment key with `if ka._1 < kb._1 || (ka._1 == kb._1 && …)` tripped `[wartremover:Equals]`. Rewrote as a pure `<`-cascade (`if a<b … else if b<a … else if …`), no equality operator. (`===` would need an `Eq` instance; the cascade is simpler.)

**`Wart.IterableOps` bans `.tail`**: `pts.zip(pts.tail)` → use `pts.zip(pts.drop(1))`.

**`s"…$f"` in a test `assert` message trips `Wart.Any`**: same StringContext.s→Any* widening as in Main/RouteExport. Use a static (non-interpolated) message string in `assert(cond, "msg")`.

**Adding a required field to a wire DTO breaks every hardcoded JSON fixture**: the new `ParamsDto.doubledPenaltyWeight` made `RoutesTest`/`ApiTest`'s literal request JSON fail to decode (missing field → 400 / decode-Left). Grep every `ParamsDto(`/`RouteParams(` positional construction and every hardcoded params JSON string when widening a DTO. Backward-compat held on the metric itself: existing straight-line test routes have `doubledFraction = 0` → penalty factor 1 → unchanged scores.

**Soft penalty > hard reject for this**: a hard filter on doubling returns nothing when every candidate is an out-and-back (dead-end start, narrow tolerance); the multiplicative `score × (1 − w·fraction)` with w,fraction ∈ [0,1] keeps the score non-negative, auto-demotes doubled routes to last, and still returns the least-bad — no fallback branch needed.

## 2026-07-20 — Penalty had to gate *membership*, not just rank

**A re-ranking penalty can't change which routes come back.** The #30 soft penalty re-scored a candidate pool that is already ≈ `numSuggestions` after `filterByDistance`+`dedupe` — so live testing showed the *same* routes at every slider setting, only re-ordered/re-scored (user caught this). Two fixes, both needed:
- **Attack generation**: A→B `viaCandidates` now sets GH's `Parameters.Routing.PASS_THROUGH` hint so a `start→via→end` request continues *through* the via instead of the cheapest answer (ride to via, U-turn, retrace). This removes most doubling at the source. Loops (`round_trip`) were already clean (live fractions 0.001–0.009) — untouched.
- **Gate membership**: `rankAndSelect` drops candidates with `doubledFraction > maxDoubled(w) = 1 − 0.7·w` *before* ranking, with a keep-all fallback if that empties the pool. Reuses the existing `doubledPenaltyWeight` — no new param. Now the slider changes the returned set, not just its order.

**`Wart.Overloading` AND `Wart.DefaultArguments` are both on.** Wanted a `blendedScore` overload taking a precomputed fraction (compute once, not twice); overloading is banned, and the fallback of a default arg (`doubled: Double = …`) is *also* banned. Solution: a distinctly-named private `scoreWith(route, params, doubled)` that the public `blendedScore(route, params)` delegates to. `rankAndSelect` pairs each route with its fraction once (`routes.map(r => (r, doubledFraction(r)))`), filters on `_._2`, and scores via `scoreWith`.

**GH `pass_through` on the tiny test fixture didn't break routing** — `DistanceTargetTest` (parallel.osm.xml, 3 nodes) stayed green with the hint set. Scala-3 parameter untupling lets `pool.map((r, f) => …)` consume a `Seq[(Route, Double)]` directly (no `case`).
