"""Blend per-way CQI / LTS / green / blue into one 0..1 routing score.

The blend is a normalised weighted sum of unit-range terms. Default weights
balance the infrastructure side (cqi + lts) against the scenic side
(green + blue), per SPEC §7.2. A `gradient` term is wired at weight 0 — the
v2-elevation seam (SPEC §7.5) — present but inert in v1.

Weights need not sum to 1: the blend divides by their total, so callers can
re-tune per request (Phase 2) and the output stays in 0..1.
"""

# cqi 0.35 + lts 0.15 = 0.50 infrastructure; green 0.25 + blue 0.25 = 0.50 scenic.
DEFAULT_WEIGHTS: dict[str, float] = {
    "cqi": 0.35,
    "lts": 0.15,
    "green": 0.25,
    "blue": 0.25,
    "gradient": 0.0,
}


def blend(
    cqi: float,
    lts: int,
    green: float,
    blue: float,
    gradient: float = 0.0,
    weights: dict[str, float] = DEFAULT_WEIGHTS,
) -> float:
    """Return a 0..1 score; higher = more preferable to route along."""
    terms = {
        "cqi": cqi / 100.0,           # 0..100 -> 0..1
        "lts": (4 - lts) / 3.0,       # LTS 1 -> 1.0, LTS 4 -> 0.0
        "green": green,
        "blue": blue,
        "gradient": gradient,
    }
    total = sum(weights.values())
    if total == 0:
        return 0.0
    score = sum(weights[k] * terms[k] for k in terms) / total
    return max(0.0, min(1.0, score))
