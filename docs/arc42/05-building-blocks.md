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

## Cross-cutting: WartRemover suppressions (Phase 2)

| Location | Wart suppressed | Reason |
|----------|----------------|--------|
| `ScoreStore.load` | `Any` | `System.err.println` widens to `Any` via `Predef` |
| `Router.route` | `Any` | Double values in string interpolation widen to `Any` |
| `Route.meanDetail` | `AsInstanceOf`, `Any`, `Equals` | `PathDetail.getValue()` returns `Object\|Null`; cast to `Double` is GH-guaranteed safe |
