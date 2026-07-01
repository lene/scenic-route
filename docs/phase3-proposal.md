# Phase 3 — Distance-Target Routing: Approach Proposal

## Spike findings (2026-07-01, Berlin graph)

Direct Brandenburg Gate → Müggelsee: **22.8 km** road distance.

**GH `round_trip` (loops):** 10 seeds × 3 targets — **100% landing rate** inside
±15% of target at 15, 20, and 30 km. Routes are distinct by seed. This is the
correct primitive for same-start/same-end loop requests.

**Via-point A→B:** Perpendicular-bisector vias, 10 samples × 2 targets:
- 30 km target: **5/10** in window (fracs 0.40–0.55 landed; 0.70+ overshot)
- 40 km target: **4/10** in window (fracs 0.70–0.85 landed; 1.00 overshot)

Road distance through a via is 30–60% longer than the straight-line via distance
(vs the 8–10% overhead on the direct route), so the upper fractions overshoot.
With 10 vias, 4–5 land; with 16 vias (adding fracs 0.30, 0.35 and both sides)
we expect 6–8 in the window — sufficient for top-4 output.

## Recommended approach

### Loop requests (start == end)
Use GH native **`round_trip`** algorithm. Request `K=12` seeds, all single-point
requests at the start. Filter to `[0.85·N, 1.15·N]`, dedupe, rank, take top 4.
GH produces distinct topology per seed. No geometry math needed.

### A→B requests (start ≠ end)
Use **perpendicular-bisector via sampling**. Place via points on both sides of the
midpoint perpendicular at fractions `f ∈ {0.30, 0.40, 0.55, 0.70, 0.85}` of
`hMax = sqrt((T/2)² − (D/2)²)` (straight-line triangle geometry, T=target, D=direct
straight-line distance). Route `start → via → end` for each via (K=10 requests).
Filter, dedupe, rank, take top 4. When `T ≤ D` (target shorter or equal to direct),
return the direct best route only.

### Shared pipeline
Both generators feed one pure pipeline:

```
candidates: Seq[Route]
  → filterByDistance([0.85·N, 1.15·N])
  → score each: infraWeight·meanCqiQuality + scenicWeight·meanScenicQuality
  → sort desc by score
  → dedupe: greedy, drop if Jaccard overlap with any already-kept route > 0.7
  → take min(numSuggestions, 5)
```

## Trade-offs vs alternatives

| Approach | Pro | Con |
|---|---|---|
| **Via-point sampling** (chosen) | Reliable distance inflation; reuses GH; tunable oversample | O(K) routing calls per request; ~50% filter loss |
| Alternative routes (`alt_route`) | One call | Only explores near-shortest; cannot inflate to 2× distance |
| k-shortest + penalty | Full coverage | No native GH primitive; heavier to build and tune |
| `round_trip` for A→B | One call | GH loop algorithm does not honour distinct endpoints |

## Risk and mitigation

**Main risk**: T close to D (A→B where target ≈ direct distance). Via offset h → 0,
few distinct routes possible. Mitigation: if `T < 1.1·D`, return the direct route
only with a note; do not return empty. This is valid — the user asked for a
distance that the graph already provides directly.

**Secondary risk**: sparse graph edges near via points → GH snaps to nearest node and
two different via coordinates collapse to the same route. Dedupe handles this
(Jaccard overlap ≈ 1.0 → dropped). May reduce candidate count below N; acceptable.

## Parameters (RouteParams additions, SPEC §7)

| Field | Default | Meaning |
|---|---|---|
| `distanceToleranceLow` | 0.85 | Lower bound as fraction of target |
| `distanceToleranceHigh` | 1.15 | Upper bound as fraction of target |
| `numSuggestions` | 4 | Returned routes (cap 5) |
| `overlapThreshold` | 0.7 | Jaccard overlap above which a route is a near-duplicate |
