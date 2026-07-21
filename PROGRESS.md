# Progress

## Current: V2 (Web UI) — Milestone C (PWA + deploy + docs)

### Status: **CHECKPOINT V2c COMPLETE — awaiting sign-off**

> V2 reopens the v1 "no UI" decision (SPEC §3/§7.4) to add a web-first UI.
> Branch `v2-web-ui`; `v2-` naming (not the v1 `phase-N` scheme).

### What's done (Milestone C)
- [x] **PWA** via `vite-plugin-pwa` (`autoUpdate`): manifest (standalone, theme, 192/512
      + maskable icons generated into `web/public`), generated service worker precaching
      the app shell (routing stays network-only). `index.html` links manifest + SW + icons.
- [x] **Responsive/mobile**: `@media (max-width: 640px)` stacks controls above the map;
      safe-area insets for notch/home-indicator; `viewport-fit=cover`.
- [x] **Deploy** (landed during B, verified here): `Dockerfile` (staged JVM server, graph
      mounted), `web/Dockerfile` (nginx serving the static bundle + reverse-proxying the API
      → single origin, no CORS), `docker-compose.yml`. nginx serves correct manifest MIME +
      `no-cache` on `sw.js`.
- [x] **CI**: new `web` job in `.github/workflows/ci.yml` (npm ci → typecheck → vitest → build).
- [x] **Docs**: arc42 `05-building-blocks.md` "Level 2: Web (V2)" (HTTP API + frontend +
      deploy); `docs/usage.md` Web UI section; SPEC §3 V2 note; DECISIONS #32 (UI reopens
      §3/§7.4) + #33 (PWA approach).

### CHECKPOINT V2c — verified live (Docker, http://localhost:8080)
```
manifest.webmanifest  → 200 application/manifest+json (name, standalone, 192/512/maskable)
sw.js                 → 200, Cache-Control: no-cache      registerSW.js → 200
icons (192/512/mask/apple/favicon) → all 200              index.html links manifest+SW+theme
/health ok · POST /routes 200 (geojson + per-route gpx) · single origin
Human gate: install as a PWA + drive on a phone-sized viewport (Add to Home Screen,
            responsive stack) — needs a real browser/device, can't run in CI/jsdom.
```

### Next step
**Await sign-off at CHECKPOINT V2c.** If signed off, V2 (Web UI) is complete — consider
merging `v2-web-ui` → `main` (open PR), then optional v-next (Capacitor native wrap,
multi-area picker, latency tuning — all deferred in the plan).

### Blocked on
Human sign-off at CHECKPOINT V2c.

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
  env config (SCENIC_AREA/HOST/PORT).

### V2 Milestone B — Frontend MVP (COMPLETE, tag `v2b-complete`, signed off)
- Vite + React + TS + MapLibre GL over the V2a API. Pure modules (`params`/`api`/`gpx`)
  + `MapView` (OSM raster, click/drag start+end, coloured ranked lines, select-highlight)
  + `Sidebar` (address search, loop toggle, essentials params, ranked list w/ per-route GPX).
- **Out-and-back handling** (follow-on, DECISIONS #30/#31): `doubledFraction` geometry
  self-overlap → soft penalty **and** membership cap; A→B `pass_through` hint at generation.
  Live-proven the returned set changes with the "Avoid backtracking" slider.
- 87 Scala tests + 16 web tests; scalafmt/scalafix/wart + tsc/build clean. Commits
  `a877a0f` (MVP), `e5ae385` (docker), `68646b9` (penalty), `1f49ed1` (membership gate).
