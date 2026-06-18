# Scenic Distance-Target Bike Route Planner — Build Specification

> **For the agent:** This is your brief. Read all of it before starting. The
> decisions in §2 are settled — do not relitigate them unless you hit a hard
> technical blocker, in which case stop and raise it. Work phase by phase (§6).
> Stop at every **CHECKPOINT** and wait for human sign-off. The §7 decisions are
> now resolved (per-request params + defaults); for any *new* decision, propose
> and wait — never silently pick.

---

## 1. Mission

A personal bicycle route planner. The user enters a **start point**, an **end
point**, and a **desired riding distance in km**, and gets back a small set of
**scenic rides covering that distance**, ranked by quality — where quality means
good cycling infrastructure **and** pleasant surroundings (proximity to green and
water). The distance target is the whole point: the user wants a *ride* of
roughly that length, not the most efficient way to arrive. The start and end may
be the **same point** — a loop ride from a single location (e.g. from home) is
explicitly supported, not only distinct A→B.

This is **not** a long-distance A→B router — those already exist. The USP is the
distance-in, scenic-selection-out experience within an area you actually ride.
Consequently a large contiguous graph is explicitly *not* the goal; coverage is
modelled as **one or more disjunct areas** (see §2).

**v1 covers one area: Berlin plus ~80 km beyond the city boundary.** Supporting
additional disjunct areas (other cities and their surroundings) is an explicit
future goal — designed for now, not built now (see §3, §5).

This is a tool for one person, run locally, optimised for **minimum effort to a
working version**. It is explicitly **not** a commercial product.

## 2. Locked decisions (do not relitigate)

These were settled deliberately. Rationale is given so you understand *why*, not
so you can reopen them.

- **Data source: OpenStreetMap only.** No Strava, no komoot. Their APIs are
  closed to third parties (Strava restricts data to the authenticated user and
  bans AI use; komoot has no public API). OSM has everything we need and no
  licensing burden.
- **No popularity / heatmap data.** Beyond being inaccessible, popularity is a
  poor quality proxy for this use case — it blends commuter and road-cyclist
  behaviour and skews toward arterials, which is exactly what we want to avoid.
  Scenic + infrastructure scoring replaces it.
- **Routing engine: GraphHopper, pinned to 11.0** (self-hosted, JVM, Apache-2.0;
  latest stable, 2025-10-14). Chosen for
  millisecond point-to-point queries (which matter because the distance-target
  feature requires generating *many* candidate routes per request), and for its
  built-in `round_trip` and alternative-route algorithms, which bootstrap the
  hard part. Called from **Scala** via normal JVM interop. The version is pinned
  because the custom `TagParser` / encoded-value / custom-model API (§5.3) is
  version-sensitive.
- **Infrastructure score: OSM Cycling Quality Index (CQI).** Reference
  implementation: https://github.com/GearKite/OSM-Cycling-Quality-Index —
  produces a 0–100 suitability value per way plus a 1–4 Level of Traffic Stress.
  NOTE: it is a QGIS Python script, not a library. Expect to **port/adapt its
  scoring logic**, not import it cleanly.
  - **CQI output is per-way but its *input* is multi-way.** The reference does
    geometric *sidepath detection* (`cycling_quality_index.py:1085`: a path is a
    sidepath if ≥2/3 of its sampled check-points lie close to road segments
    sharing OSM ID / highway class / street name), then inherits that parallel
    road's effective `highway` class and `maxspeed` to compute the score
    (`:2861`, `:2970`). So scoring a way reads *neighbouring* ways' tags. This
    drives two consequences: the pipeline needs a spatial join (§5.2), and CQI
    tests need a cycleway-plus-parallel-road fixture, not isolated tag classes
    (§9).
- **Scenic score: per-way green/blue proximity, computed offline from OSM.**
  Buffer each way and measure overlap with / adjacency to green and water
  features. Precomputed once into the global way-ID store and attached to edges
  at build time — never computed at routing time.
- **Coverage = disjunct areas, never one big graph.** An *area* is a routing
  graph over a bounded region (v1: Berlin + ~80 km). The model is several such
  areas, each independent — not a growing contiguous map. Rationale: every query
  is distance-bounded (an N-km ride explores only a local subgraph), so a bigger
  graph buys nothing for routing and only inflates build and memory cost. This is
  a short-ride tool; long-distance routing is a non-goal. Adding an area must be
  additive (config + selection), never a refactor — so **parameterise the area
  from the start** (extract source and clip polygon as config, region identity
  explicit) and do not hardcode "Berlin" anywhere.
- **Scoring is per-way and area-independent — one global store, keyed by OSM way
  ID.** Do *not* treat scoring as a per-area batch. Compute scores once over the
  **union** of ways you cover and store them keyed by way ID; any area's graph
  reads from that one store. This makes overlapping areas free (a shared way is
  one entry, deduped automatically), avoids double-processing, and — critically —
  avoids boundary artefacts (see §5.2). Scoring cost then equals the number of
  *distinct ways covered* — the quantity to control — and is independent of how
  areas are carved up. No planet-wide reprocessing, no incremental-sync plumbing;
  a manual re-run on OSM refresh is fine and expected to be rare.
- **License: GPL-3.0.** The CQI logic is ported from GearKite/
  OSM-Cycling-Quality-Index, which is **GPL-3.0**; a close port is a derivative,
  and this repo is public, so the project ships under **GPL-3.0** (relicensed
  from MIT). Permissive deps (GraphHopper Apache-2.0, Scala, PostGIS) import fine
  into GPLv3 — the only thing to watch is an incompatible-copyleft *runtime* dep
  (EPL/CDDL/MPL-1.1/GPLv2-only), of which this stack has none. Attribute GearKite
  and the upstream SupaplexOSM Cycling Quality Index in the ported files.

## 3. Non-goals (out of scope — do not build)

- Any commercial, multi-user, auth, or hosting concerns.
- **Building** the multi-area selector/orchestration in v1 — it's a future goal,
  so the design must leave the seam (§2), but only one area ships now. A
  contiguous country- or continent-scale graph is out of scope *permanently*:
  that's the long-distance-router problem this tool deliberately isn't.
- Incremental/automated OSM sync pipelines.
- Popularity, social, or activity-feed features.
- Any UI. v1 output is files only — **GPX + GeoJSON, no map UI** (§7.4). GeoJSON
  is the checkpoint-verification artefact (drag onto geojson.io to eyeball); a
  map app earns its place only if that proves painful.
- Turn-by-turn navigation / voice / mobile app.

## 4. Environment & stack assumptions

- Developer is a **JVM/Scala** developer; Scala is the implementation language
  for the routing service and distance-target logic.
- **PostgreSQL + PostGIS** is available and is the natural place for the offline
  geometry scoring (buffer/overlap) — and now doubly so, because CQI sidepath
  detection (§2) needs a way↔nearby-road spatial join, which a spatial DB does
  cleanly. Python with GeoPandas/Shapely is an acceptable alternative if simpler;
  final choice logged in `DECISIONS.md`.
- Fish shell on Ubuntu; NVIDIA GPU present but **not needed** for this project.
- Prefer boring, working solutions over clever ones. This is a personal tool.

## 5. Architecture

Five components, in dependency order. Keep them loosely coupled.

1. **Data acquisition.** The v1 area is Berlin + ~80 km beyond the city
   boundary. Obtain the Berlin and Brandenburg extracts from Geofabrik
   (`berlin-latest.osm.pbf`, `brandenburg-latest.osm.pbf` — 80 km from the Berlin
   boundary stays essentially within Brandenburg), then clip to an **80 km buffer
   around Berlin's administrative boundary** using `osmium extract` with a polygon.
   The extract source and clip polygon are config, not hardcoded — adding another
   area later is new config, not new code.

2. **Offline scoring pipeline** (Python/PostGIS). Consumes the extract, produces
   a table/file keyed by **OSM way ID**:
   `way_id -> { cqi: 0..100, lts: 1..4, green: 0..1, blue: 0..1, score: 0..1 }`.
   - `cqi`/`lts` from ported CQI logic. This includes the sidepath spatial join
     (§2): match each candidate path to its parallel road and inherit that road's
     `highway`/`maxspeed` before scoring. PostGIS does this join naturally.
   - `green`/`blue` from buffer-overlap against OSM green features
     (`leisure=park|garden|nature_reserve|recreation_ground`,
     `landuse=forest|meadow|grass|village_green|allotments`,
     `natural=wood|grassland|heath|scrub`, `boundary=national_park|protected_area`)
     and water features (`natural=water`, `waterway=river|stream|canal`).
   - `score` = a documented blend of the above (default weights in §7.2).
   - **Avoid double-counting:** a `highway=cycleway` through a park already
     scores well on CQI; tune so scenic bonus complements rather than dominates.
   - **Emit a portable flat file** keyed by way ID (e.g. CSV/Parquet:
     `way_id -> {cqi,lts,green,blue,score}`) as the handoff to the JVM import
     (§5.3). Compute in PostGIS, hand off a file — the GraphHopper build must not
     depend on a live DB connection.
   - **Score the union once, keyed by way ID (see §2).** The output store is
     global and area-independent. When scoring an area, build the green/blue
     feature layer from features extending **beyond** the way set (buffer the
     feature query area past the area boundary) so edge ways still see nearby
     parks/water lying just outside the routing extent — otherwise boundary ways
     get wrongly deflated scenic scores. Scoring is idempotent per way: never
     re-score a way an existing area already covered.

3. **GraphHopper integration.** A custom build step attaches the precomputed
   per-way score to GraphHopper edges. **Recommended approach to avoid an
   edge-geometry-matching nightmare:** key scores by OSM way ID, write a custom
   `TagParser` / import hook that looks up each way's score (from the global
   way-ID store, §2) during import and
   stores it in a custom `DecimalEncodedValue` (e.g. `scenic_quality`). The
   `TagParser` reads the OSM way ID from the `ReaderWay` during import and looks
   the score up in the flat file (§5.2). A custom
   model (weighting) then routes on that encoded value. Precedent exists — see
   GraphHopper's "greener routing" / per-edge-AQI community examples. Target the
   **GraphHopper 11.0** API (§2) — this surface is version-sensitive.
   - **Known modelling limitation (accepted for v1):** GraphHopper splits one OSM
     way into many edges; every edge from a way inherits the *same* per-way
     score. A long way that dips in and out of green gets one uniform value. This
     is accepted — finer-grained per-edge scoring is out of scope for v1.
   - Use **hybrid/landmark (LM) mode** during development so weights are
     tunable per-request. Switch to **CH** only once weights are frozen, for
     full speed. Do not start with CH — it bakes weights in at build time.

4. **Routing service** (Scala). Wraps the GraphHopper instance. Exposes a clean
   internal function roughly:
   `route(start: LatLon, end: LatLon, targetKm: Double, params: RouteParams): Seq[RankedRoute]`,
   where `RouteParams` carries the per-request knobs from §7 (distance-tolerance
   bounds, scoring weights, number of suggestions) and supplies the documented
   defaults. `start == end` is valid — a loop request (§1).

5. **Distance-target layer** (Scala) — **the genuinely hard part, treat as a
   spike, see Phase 3.** Standard routing minimises cost; producing an A→B route
   of a *target length* is a detour/loop problem with no native solution.

6. **Output** (thin). Ranked routes as **GPX + GeoJSON** files. No map UI (§7.4).

## 6. Build phases (stop at every CHECKPOINT)

Commit at the end of each phase with a clear message. At each checkpoint: tag the
commit (`phase-N-complete`), update `PROGRESS.md` and append any learnings to
`JOURNAL.md` (§9), then post a short summary of what was built with concrete
evidence it meets the acceptance criteria, and **wait for sign-off** before
continuing.

**Phase 0 — Skeleton & vanilla routing.**
Set up the project, stand up the CI skeleton and test harness (compile + test on
push, §9), acquire and clip the v1 area extract (§5.1), stand up GraphHopper with
the stock bike profile, route between two points in the area from Scala. Treat
the area as a config-driven parameter from this first commit.
*Acceptance:* a Scala call returns a valid A→B bike route within the
Berlin + 80 km area using unmodified GraphHopper, and CI runs green on push.
→ **CHECKPOINT 0**

**Phase 1 — Offline scoring pipeline.**
Build component 2. Port CQI logic; compute green/blue; emit the per-way score
table.
*Acceptance:* a score table covering the area's ways in the global way-ID store,
with spot-checks that pass human sanity — e.g. a canal towpath or a path through
the Grunewald scores high on scenic; a busy Hauptstraße scores low on CQI. Show
5–10 named examples with their scores.
→ **CHECKPOINT 1** (human reviews scoring sanity — this is where bad scoring is
cheapest to catch)

**Phase 2 — Score-aware routing.**
Build component 3. Attach scores via custom encoded value; add the custom-model
weighting; route in hybrid mode.
*Acceptance:* for a fixed start/end, show the route *before* vs *after* scoring
and demonstrate it now prefers higher-scoring ways. Weights are adjustable
without a full rebuild.
→ **CHECKPOINT 2**

**Phase 3 — Distance-target routing (SPIKE FIRST).**
Before building, **write a short proposal** (≤1 page) for how you'll hit the
target distance A→B — e.g. via-point sampling, loop/detour variants adapted from
`round_trip`, or k-shortest-paths with detour penalties — with trade-offs, and
**wait for sign-off on the approach**. Then implement: generate candidates,
filter to those near target length, rank by blended cost, return the top N.
*Acceptance:* given start, end, and N km, returns several distinct routes within
the agreed distance tolerance, ranked, each with its score.
→ **CHECKPOINT 3a** (approach proposal) and **CHECKPOINT 3b** (implementation)

**Phase 4 — Output (minimal).**
GPX + GeoJSON export. No map UI (§7.4 resolved).
→ **CHECKPOINT 4**

## 7. Request parameters & resolved decisions

These were the open decisions; all are now resolved. **1–3 are per-request
parameters** (`RouteParams`, §5.4) — defaults below, overridable on every call,
never hardcoded into a request path.

1. **Distance tolerance.** Soft target N with a hard outer bound. *Default:*
   reject outside `[0.85·N, 1.15·N]`; within the window, score pulls the choice.
2. **Scenic/CQI weight blend.** *Default:* CQI ≈ scenic (roughly equal). Gradient
   is deferred (§5/§7.5) — its term exists in the custom model at **weight 0** as
   a seam, no real elevation in v1. Concrete numbers are tuned and frozen at
   **CHECKPOINT 2** with real routes.
3. **Number of suggestions.** *Default:* 4 (cap 5); dedupe near-identical routes
   (path-overlap threshold) so the set isn't clones.
4. **Output scope.** **GPX + GeoJSON files, no UI** (resolved; see §3, §5.6).
5. **Elevation/gradient.** **Deferred to v2.** Berlin + Brandenburg is essentially
   flat, so gradient buys little in the v1 area, and elevation adds a DEM data
   dependency. Keep the zero-weighted gradient seam (item 2); wire real elevation
   when an area with relief is added.

## 8. Rules of engagement

- **Phase gates are hard stops.** Never roll past a CHECKPOINT without sign-off.
- **Propose, don't assume.** The §7 items are resolved, but the principle stands
  for any new decision: bring it to the human, don't silently pick.
- **Keep a `DECISIONS.md` log** of every non-trivial choice you make (including
  the ones delegated to you, like Python-vs-PostGIS), with one-line rationale.
- **When blocked or facing ambiguity not covered here, stop and ask** — a cheap
  question beats an expensive wrong guess. Do not invent requirements.
- **Locked decisions (§2) stand** unless you hit a hard blocker; then raise it,
  don't quietly work around it.
- **Simplicity is a feature.** No premature abstraction, no dependencies you
  can't justify. The *only* forward-looking allowance is the area seam in §2
  (parameterise the area; global way-ID score store) — build that, but build
  nothing else speculatively. Test and CI infrastructure (§9) is core, not
  speculative — it stands. This is a personal tool.
- **Verify your own work** wherever you can — spot-check scores, compare routes,
  sanity-check distances — and show that evidence at checkpoints.

## 9. Engineering practices

### Test-driven development

TDD is mandatory. The *method* (red-green-refactor and the rest) is governed by
`CLAUDE.md` and the superpowers plugin — follow those; this section only pins
down what TDD means for *this* project and deliberately does not restate the
methodology.

- **Test-first on the deterministic mechanics**, which is most of the code:
  config/area loading, the `osmium` clip, CQI scoring per tag class, green/blue
  buffer-overlap, the score blend, the GraphHopper import hook (encoded value is
  attached and reads back correctly), distance-window filtering, ranking order.
  These all have known-answer tests. CQI fixtures must include a
  **cycleway plus a parallel road** so the sidepath-derived `highway`/`maxspeed`
  path (§2) is covered, not just isolated single-way tag classes.
- **Use small fixtures, never the full dataset.** A handful of hand-built ways
  with known tags → known scores; a tiny known graph → known route properties.
  Tests must run in seconds and must not depend on the Berlin + 80 km build.
- **Do not try to unit-test subjective quality.** "Is this a *good* scenic ride?"
  is not a test assertion — that is exactly what the human checkpoints (1 and 3)
  are for. TDD covers correctness; checkpoints cover taste. Don't write brittle
  tests asserting an aesthetic; do test the mechanics that produce it (e.g. a
  route through a higher-scenic corridor comes out with a lower blended cost).
- **The Phase 3 spike is exempt from test-first.** A spike is throwaway
  exploration to choose an approach. Once the approach is signed off at
  Checkpoint 3a, the real implementation is built test-first like everything else.

### Commits & pushes (granularity)

A checkpoint is *not* a commit — it is many. Commit far smaller than a phase.

- **One commit per green red-green-refactor cycle, or per coherent working unit**
  (a function + its tests, the clip script, one CQI tag-class, the store writer).
  Rough heuristic: more than a few files or a few hundred lines usually means the
  commit is doing too much — split it. The unit is "one coherent, reviewable,
  working change," not a line count.
- **Every commit is green** — compiles, tests pass. Never commit a broken tree.
- **Push at every green commit** (or at least once per working session). It's a
  personal repo; frequent pushes are free backup and trigger CI.
- **Imperative messages referencing the phase**, e.g.
  `Phase 1: green-feature buffer-overlap scoring`.
- For scale: expect roughly **6–12 commits per phase**, not one. The final commit
  of a phase leaves the tree in the acceptance-criteria-met state and is what you
  present at the checkpoint.

### Continuous integration

Wanted, and kept minimum-effort: a single GitHub Actions workflow.

- **Run the full test suite on every push**, plus compile and a format check
  (scalafmt). This is **two languages**: the Scala routing/distance-target tests
  *and* the Python scoring-pipeline tests both run in CI. Fast feedback is the
  whole point — rely on fixtures to keep it under a couple of minutes.
- **Integration tests that need PostGIS** use a GitHub Actions service container;
  pure-logic unit tests need no database — keep the DB off the hot path.
- **Do NOT run the full Berlin + 80 km data build in CI per push.** It's the heavy
  batch step (§2) and would wreck feedback time. CI tests the preprocessing
  *logic* on tiny fixtures; the full *data build* stays a manual/occasional job,
  consistent with the "rare reprocess" decision. *Optional, not required for v1:*
  a separate **scheduled** workflow (e.g. monthly) that runs the full
  preprocessing against current OSM as a smoke test and/or to refresh the artifact.
- CI scaffolding (compile + test on push) is part of **Phase 0**, so TDD and CI
  are in place before any real logic lands.

### Working state & continuity (across sessions and machines)

Claude Code's own session history is local to one machine (under `~/.claude/`, not
in the repo) and cannot be resumed elsewhere. So **everything needed to resume on
a fresh checkout must be committed.** Three small repo files carry that state:

- **`PROGRESS.md`** — *overwritten* each increment. Current phase, what's done,
  what's in flight, the single next step, and anything blocked awaiting a named
  checkpoint sign-off. Keep it short. This is the "where do I start" anchor, read
  first every session.
- **`JOURNAL.md`** — *append-only*. Discoveries, dead ends, gotchas, approaches
  tried and rejected, surprises ("tried X, abandoned because Y"). This is the
  knowledge that has no home in code and otherwise evaporates between sessions.
  Distinct from `DECISIONS.md`, which records chosen directions; the journal
  records what was *learned*, including negative results.
- **`DECISIONS.md`** — as already specced (§8): chosen directions, one-line
  rationale each.

Guard against drift: `PROGRESS.md` is only trustworthy because it can be
cross-checked against ground truth — the **git tag at each checkpoint**
(`phase-1-complete`, …), the phase-prefixed commit messages, and the **test
suite**. A fresh checkout reconstructs intent from these files and *verifies* it
by running the tests green before resuming.

**Bootstrap ritual** (encoded in `CLAUDE.md` so it fires automatically at session
start; documented here so the process is explicit): read `PROGRESS.md`,
`DECISIONS.md`, `JOURNAL.md`; check `git log` and tags; run the test suite to
confirm the tree is green and matches the claimed state; then resume at the single
next step in `PROGRESS.md`. If the tests are not green, or reality contradicts
`PROGRESS.md`, stop and surface it before doing anything else.

## 10. Definition of done (v1)

From a Scala entry point: given a start and end within the covered area
(Berlin + 80 km) — start and end may be the same point for a loop — and a target
distance, the tool returns a ranked handful of
rideable routes near that distance that visibly favour good infrastructure and
green/blue surroundings over bare arterials — exportable as **GPX + GeoJSON** —
with the scoring and weighting transparent and tunable per request, scores held
in the global way-ID store, and every notable decision logged. Ships **GPL-3.0**.
