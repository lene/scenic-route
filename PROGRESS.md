# Progress

## Current: V2 (Web UI) — Milestone B (Frontend MVP)

### Status: **CHECKPOINT V2b COMPLETE — awaiting sign-off**

> V2 reopens the v1 "no UI" decision (SPEC §3/§7.4) to add a web-first UI.
> Branch `v2-web-ui`; `v2-` naming (not the v1 `phase-N` scheme).

### What's done (Milestone B — frontend MVP)
- [x] Scaffold `web/` (Vite + React 18 + TS + MapLibre GL + vitest/jsdom/testing-library)
- [x] Pure modules, test-first:
  - `params.ts` — sidebar controls → `ParamsDto` (balance→infra/scenic; ±pct→low/high band, clamped so low>0; suggestions rounded/clamped to [1,5]). Always satisfies backend band validation.
  - `api.ts` — typed `findRoutes` (POST /routes) + `geocode` (GET /geocode); same-origin in dev (Vite proxy), `VITE_API_BASE` in prod; surfaces server error text.
  - `gpx.ts` — `gpxBlob` + `downloadGpx` (client-side blob save; GPX comes inline in the /routes response).
- [x] React UI:
  - `MapView.tsx` — MapLibre OSM raster + attribution; click-to-place + draggable start/end markers; renders the returned FeatureCollection coloured by simplestyle `stroke`; selected route widened, others dimmed.
  - `Sidebar.tsx` — address search (→ /geocode), active-point (start/end) selector, loop toggle, essentials params (target km, infra↔scenic, ±tolerance, suggestions), Find button (guarded), ranked-route list with colour swatch + per-route ⬇ GPX + hover-to-highlight.
  - `App.tsx` — state + wiring; loop sends `end = start`; hardcoded Berlin centre (`// ponytail:` expose via /health when multi-area lands).
- [x] 15 frontend tests green (params 6, api 3, gpx 2, Sidebar 4); `tsc -b` clean; `vite build` clean.
- [x] Dev proxy wired: `/routes`,`/geocode`,`/health` → `http://localhost:8080` (Server default).

### CHECKPOINT V2b — closed here (unit/build) vs. deferred to sign-off (browser)
```
Closed here:  typecheck clean · vite build clean · 15 vitest green · proxy config correct
Human gate:   in a browser vs the local API — set two points (or search), pick a
              distance, Find → coloured ranked routes on the map, download one as GPX.
              (Map WebGL rendering + React events can't run in jsdom; this is the
              plan's Milestone B acceptance and a hard-stop checkpoint.)
```

### To run the full stack locally (for the browser acceptance)
```
# Option A — Docker (single origin, matches deploy). nginx serves the app and
# reverse-proxies the API to the JVM backend; graph/data/areas mounted as volumes.
sbt stage                                   # package backend → target/universal/stage
docker compose up --build                   # open http://localhost:8080

# Option B — dev servers
sbt "runMain scenicroute.Server"            # :8080 backend, ~18s boot
cd web ; npm run dev                        # :5173 frontend (Vite proxy → :8080)
```
Docker stack **verified live**: `/health` ok · `/geocode` 5 hits · `POST /routes` 200 in ~3.5s
(GeoJSON + per-route GPX) · SPA served at `:8080`. (Dockerfiles are a Milestone C
deliverable, pulled forward here to run the browser acceptance.)

### Next step
**Await sign-off at CHECKPOINT V2b**, then start **Milestone C — PWA + deploy + docs**
(vite-plugin-pwa manifest/SW; responsive/mobile; Dockerfile + docker-compose;
CORS/origin config; CI `web` job; docs + arc42 frontend/api; DECISIONS #30 + SPEC note).

### Blocked on
Human sign-off before Milestone C.

---

## Shipped

### v1 (COMPLETE, merged to main, tags `phase-1..4-complete`)
- Phase 1: Python scoring pipeline — 678,459 Berlin ways scored
- Phase 2: score-aware routing (custom EVs + per-request blend)
- Phase 3: distance-target routing (via-sampling A→B + round_trip loops → filter/rank/dedupe)
- Phase 4: GPX + GeoJSON file export + CLI
- v1 definition-of-done (SPEC §10) met.

### V2 Milestone A — Backend API (COMPLETE, commit `9c495f1`, live-verified)
- http4s ember + tapir + circe. `POST /routes` (GeoJSON + per-route GPX), `GET /geocode`
  (Nominatim proxy), `GET /health`. Routing/geocoding injected → HTTP layer unit-tested
  without a graph/network. `Server` composition root loads the area graph once, allow-all CORS,
  env config (SCENIC_AREA/HOST/PORT). 76 Scala tests green; scalafmt/scalafix/wart clean.
