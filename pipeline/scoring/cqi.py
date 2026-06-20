"""CQI assembly with sidepath inheritance (SPEC §2, DECISIONS #13).

scoring.tags scores a way from its own tags, treating a separated path as
context-free. But a cycleway hugging a main road is a *sidepath*: its real
quality is shaped by that road (noise, fumes, crossings). When scoring.sidepath
identifies the parallel road, we moderate the path's otherwise-ideal score by
the road's speed — a milder penalty than riding in traffic, because the path is
still separated, so LTS stays low.

This is the pragmatic stand-in for the reference's "inherit the parallel road's
highway/maxspeed": we use the inherited maxspeed as an environment modifier
rather than re-deriving the base class (which would wrongly collapse a separated
cycleway to a primary-road score).
"""

from dataclasses import dataclass

from scoring.tags import _SEPARATED, _parse_maxspeed, cqi_from_tags, lts_from_tags


@dataclass(frozen=True)
class Parallel:
    """The road a sidepath runs alongside, whose context it inherits."""

    highway: str | None
    maxspeed: str | None


def _sidepath_environment_factor(maxspeed: str | None) -> float:
    """How much an adjacent road's speed dampens a separated path's score."""
    speed = _parse_maxspeed(maxspeed)
    if speed is None:
        return 1.0
    if speed <= 30:
        return 1.0
    if speed <= 50:
        return 0.85
    if speed <= 70:
        return 0.75
    return 0.65


def score(tags: dict[str, str], parallel: Parallel | None = None) -> tuple[float, int]:
    """Return (cqi 0..100, lts 1..4) for a way, applying sidepath context."""
    cqi = cqi_from_tags(tags)
    lts = lts_from_tags(tags)
    if parallel is not None and tags.get("highway") in _SEPARATED:
        cqi *= _sidepath_environment_factor(parallel.maxspeed)
    return max(0.0, min(100.0, cqi)), lts
