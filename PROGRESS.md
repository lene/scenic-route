# Progress

## Current: V2 (Web UI) — Milestone A (Backend API)

### Status: **CHECKPOINT V2a COMPLETE — awaiting sign-off**

> V2 reopens the v1 "no UI" decision (SPEC §3/§7.4) to add a web-first UI.
> Branch `v2-web-ui`; `v2-` naming (not the v1 `phase-N` scheme).

### What's done (Milestone A — backend API)
- [x] Deps: http4s ember (server+client) + tapir + circe, resolved for Scala 3.6.4
- [x] `Api`: wire DTOs + circe codecs; pure `toDomain` (validation) + `toResponse` (GeoJSON + per-route GPX), reusing `RouteExport`
- [x] `Routes`: tapir `POST /routes`, `GET /geocode`, `GET /health`; routing + geocoding **injected** → HTTP layer tested without a graph/network
- [x] `Geocode.parseNominatim`: pure Nominatim jsonv2 parser (string lat/lon → Double, drops malformed)
- [x] `Server`: IOApp composition root — loads area graph once, live Nominatim client, allow-all CORS, env config (SCENIC_AREA/HOST/PORT)
- [x] 76 Scala tests green; scalafmt + scalafix + WartRemover clean

### CHECKPOINT V2a evidence (live, Berlin graph, port 8080)
```
server UP (~18s)
GET  /health                     → ok
POST /routes (a2b 30km)          → FeatureCollection + 4 routes
   rank 1 30272 m score 0.465   … each route carries its own <?xml GPX
   rank 2 28130 m score 0.464
   rank 3 32110 m score 0.457
   rank 4 28927 m score 0.456
POST /routes (targetKm=0)        → 400
GET  /geocode?q=Brandenburger+Tor→ 5 matches (52.5162699, 13.3777034)
```

### Next step
**Await sign-off at CHECKPOINT V2a**, then start **Milestone B — Frontend MVP**
(Vite + React + TS + MapLibre; click/drag/search points; essentials params; render
ranked routes; per-route GPX download).

### Blocked on
Human sign-off before Milestone B.

---

## Shipped

### v1 (COMPLETE, merged to main, tags `phase-1..4-complete`)
- Phase 1: Python scoring pipeline — 678,459 Berlin ways scored
- Phase 2: score-aware routing (custom EVs + per-request blend)
- Phase 3: distance-target routing (via-sampling A→B + round_trip loops → filter/rank/dedupe)
- Phase 4: GPX + GeoJSON file export + CLI
- v1 definition-of-done (SPEC §10) met.
