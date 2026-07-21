# Usage

scenic-route takes a start, an end, and a target riding distance, and returns a
small ranked set of scenic rides of roughly that length. The **CLI** (v1) writes
them as **GPX + GeoJSON** files; the **web UI** (V2) shows them on a map and exports
each as GPX. (The start and end may be the same point — a loop from home.)

## Web UI (V2)

The quickest way to run the whole thing is Docker — nginx serves the app and
reverse-proxies the API, so the browser sees one origin:

```
sbt stage                    # package the backend → target/universal/stage
docker compose up --build    # then open http://localhost:8080
```

For frontend development against a locally running backend:

```
sbt "runMain scenicroute.Server"   # API on :8080 (~18 s to load the Berlin graph)
cd web && npm install && npm run dev   # UI on :5173 (Vite proxies the API to :8080)
```

In the app: search an address or click the map to set **start** and **end** (or toggle
**Loop from start**), pick a **target distance**, adjust the **essentials**
(infra↔scenic balance, ±tolerance, suggestions, avoid-backtracking), press **Find
routes**, then download any ride as **GPX**. It installs as a PWA (Add to Home Screen)
and is usable on a phone; routing itself needs a network connection.

## CLI (v1)

```
sbt "runMain scenicroute.Main <areaToml> <startLat,startLon> <endLat,endLon> <targetKm>"
```

Example — a ~30 km A→B ride across Berlin:

```
sbt "runMain scenicroute.Main areas/berlin.toml 52.5163,13.3777 52.4275,13.6517 30"
```

Example — a ~20 km loop from one point (start == end):

```
sbt "runMain scenicroute.Main areas/berlin.toml 52.5163,13.3777 52.5163,13.3777 20"
```

Running with no (or unparseable) arguments falls back to a built-in Berlin demo.

## Output

Two combined files land under `out/<area>/`:

- `<mode>-<km>km.geojson` — one FeatureCollection with all suggested routes, each
  a coloured LineString carrying `rank`, `distance_m`, `blended_score`,
  `mean_cqi`, `mean_scenic`. **Drag it onto [geojson.io](https://geojson.io) to
  eyeball the set.**
- `<mode>-<km>km.gpx` — one GPX track per route, for loading onto a device.

`<mode>` is `loop` when start ≈ end, otherwise `a2b`.

Routes are ranked by a blended CQI/scenic score, kept within ±15% of the target
distance, and de-duplicated so the set isn't near-identical clones. Rides that pad
their distance by going out along a road and back are avoided at two levels: A→B
candidates are generated with GraphHopper's `pass_through` hint (no U-turn at the via),
and the ranker penalizes *and*, at higher `doubledPenaltyWeight` (default 0.8),
**excludes** routes that still double back. The scoring weights, tolerances, and
backtracking penalty are per-request knobs (`RouteParams`) and need no graph rebuild to
change; the web UI exposes the penalty as an "Avoid backtracking" slider that changes
which routes come back, not just their order.
