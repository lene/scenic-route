# Arc42 §5 — Building Block View

## Level 1: scenic-route system

```
┌─────────────────────────────────────────────────────────────┐
│                        scenic-route                         │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────────┐  │
│  │  ScoreStore  │   │    Router    │   │      Main      │  │
│  │  (CSV load)  │──▶│  (GH embed)  │──▶│  (CLI / demo)  │  │
│  └──────────────┘   └──────────────┘   └────────────────┘  │
│         │                  │                                 │
│  ┌──────▼──────────────────▼──────┐                         │
│  │     ScenicImportRegistry       │                         │
│  │  ScenicTagParser × {cqi,scenic}│                         │
│  └────────────────────────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

---

## Level 2: Score-aware routing (Phase 2)

### ScoreStore

Reads `data/<area>-scores.csv` (columns: `way_id, cqi, lts, green, blue, score`)
produced by the Python pipeline in Phase 1.

`deriveSignals(cqi, lts, green, blue): WayQuality` computes two encoded-value inputs
from the raw CSV columns:

```
cqiQuality   = 0.7 × (cqi / 100) + 0.3 × ((4 − lts) / 3)   ∈ [0, 1]
scenicQuality = 0.5 × green + 0.5 × blue                     ∈ [0, 1]
```

`load(path): Map[Long, WayQuality]` returns an immutable way-id → quality map.
Missing or unreadable file → empty map + stderr log (graceful degradation).

### ScenicImportRegistry / ScenicTagParser

GH 11.0 custom encoded value pipeline:

```
ScenicImportRegistry.createImportUnit(name)
  → ImportUnit.create(name, createEV, createTagParser)
       createEV:        DecimalEncodedValueImpl(name, 7, 0.01, false)
                        (7 bits, 0.01 resolution, 0..1.27 range, one direction)
       createTagParser: ScenicTagParser(ev, scores, pick)
                        handleWayTags: ev.setDecimal(false, edgeId, access,
                                           scores.get(wayId).fold(0.0)(pick))
```

Two units are registered: `cqi_quality` (picks `_.cqiQuality`) and
`scenic_quality` (picks `_.scenicQuality`).

**Ordering constraint**: `gh.setImportRegistry(registry)` must be called before
`gh.init(config)`. Calling after `init()` silently skips registration.

Activated via config: `graph.encoded_values = "cqi_quality,scenic_quality"`.

### Router

`fromOsm(osmFile, graphCache, scoreFile)` loads scores and wires the registry
before building the GH instance in LM (landmark/hybrid) mode.

`route(start, end, params: RouteParams)` builds a per-request `CustomModel`
using two successive MULTIPLY priority statements — a multiplicative proxy for
the additive blend (see DECISIONS #19):

```
priority × = (0.2 + wInfra  × cqi_quality)
priority × = (0.2 + wScenic × scenic_quality)
```

The 0.2 floor ensures even low-scoring roads remain traversable (never zero
priority). The profile-level speed cap of 15 km/h is set once at import and
does not change per request.

Path details are requested for both EVs; `Route.meanCqiQuality` /
`meanScenicQuality` are length-weighted means over the returned path segments.

### RouteParams

```scala
final case class RouteParams(infraWeight: Double, scenicWeight: Double, gradientWeight: Double)
```

`default = (0.5, 0.5, 0.0)` — equal CQI/scenic weighting; gradient weight is
a zero-value seam (no elevation EV in v1; wire real DEM in v2).

---

---

## Level 2: Distance-target routing (Phase 3)

### RouteSelection

Pure pipeline (no GH dependency) — all functions known-answer testable:

```
filterByDistance(routes, targetM, params) → keeps dist ∈ [low·target, high·target]
blendedScore(route, params) = infraWeight·meanCqiQuality + scenicWeight·meanScenicQuality
overlap(a, b) = Jaccard of rounded lat/lon point sets (ponytail: swap for edge-id sets if too coarse)
dedupe(ranked, threshold) = greedy in rank order; drop if overlap > threshold with any kept route
rankAndSelect(routes, params) = score → sort desc → dedupe → take min(numSuggestions, 5)
```

### RankedRoute

`final case class RankedRoute(route: Route, blendedScore: Double)`. Position in returned `Seq` is the rank.

### Router — distance-target extensions

**`routeWithTarget(start, end, targetKm, params): Seq[RankedRoute]`**

Dispatches on `start == end`:
- **Loop** (`start == end`): `loopCandidates` — 12 seeds × GH `round_trip` algorithm.
  Single-point request; `Parameters.Algorithms.ROUND_TRIP` + hints `DISTANCE`, `SEED`, `POINTS`.
- **A→B** (`start ≠ end`): `viaCandidates` — perpendicular-bisector via sampling.
  Fracs `{0.30, 0.40, 0.55, 0.70, 0.85}` × both sides = 10 vias; route `start → via → end`.
  Via offset: `h = sqrt((T/2)² − (D/2)²)` where T=target, D=straight-line distance.

Both generators feed: `filterByDistance → rankAndSelect`.

**Spike results (Berlin, Brandenburg Gate → Müggelsee):**
- `round_trip`: 100% landing rate (10/10 per target) at 15, 20, 30 km
- Via-point: 40–50% landing rate (4–5/10) at 30, 40 km; 10 vias yields sufficient candidates for top-4

---

## Level 2: Output (Phase 4)

### RouteExport

Pure `String`-producing serialisers (no GraphHopper dependency, known-answer tested):

- `toGeoJson(routes)` → one `FeatureCollection`; each route a `LineString`
  `Feature` in **[lon, lat]** order (RFC 7946) with properties
  `rank, distance_m, blended_score, mean_cqi, mean_scenic` plus simplestyle
  `stroke` (per-rank colour) + `stroke-width` so geojson.io renders the ranked
  set in distinct colours.
- `toGpx(routes, name)` → one `<gpx version="1.1">` with one `<trk>` per route;
  `<trkpt>` only (no `<ele>` — flat area, zero-weight gradient seam, SPEC §7.5).
  Track name is XML-escaped.

All numeric formatting is pinned to `Locale.ROOT`: the dev JVM default locale is
German, where `%.6f` emits `13,377` and corrupts both formats (DECISIONS #28).

### Main — CLI + file writing

`main` parses positional args `<areaToml> <lat,lon> <lat,lon> <targetKm>`
(`parseArgs` / `parseLatLon` — pure, `Option`-returning, range-checked). A valid
request routes and writes the **combined** files
`out/<area>/<mode>-<km>km.{geojson,gpx}` where `mode = loop | a2b` (loop when
start ≈ end). Anything else falls back to the built-in Berlin demo, which now
also writes its A→B (30 km) + loop (20 km) exports. `out/` is git-ignored.

---

## Cross-cutting: WartRemover suppressions (Phase 2)

| Location | Wart suppressed | Reason |
|----------|----------------|--------|
| `ScoreStore.load` | `Any` | `System.err.println` widens to `Any` via `Predef` |
| `Router.route` | `Any` | Double values in string interpolation widen to `Any` |
| `Route.meanDetail` | `AsInstanceOf`, `Any`, `Equals` | `PathDetail.getValue()` returns `Object\|Null`; cast to `Double` is GH-guaranteed safe |
