"""Buffer-overlap scenic scoring (SPEC §5.2).

A way's scenic value is how much of its immediate corridor lies in green /
water surroundings. We buffer the way line into a corridor and measure the
fraction of that corridor covered by green / blue features -> two values in
0..1. Buffering both captures ways *through* a feature (high overlap) and ways
*alongside* one (the corridor catches the near strip).

Geometries must be in a metric CRS (metres) so the buffer distance is real;
the pipeline projects everything to EPSG:25833 once before calling.
"""

from shapely.geometry.base import BaseGeometry


def _overlap_fraction(corridor: BaseGeometry, features: BaseGeometry | None) -> float:
    if features is None or features.is_empty or corridor.area == 0.0:
        return 0.0
    inter = corridor.intersection(features)
    if inter.is_empty:
        return 0.0
    return max(0.0, min(1.0, inter.area / corridor.area))


def scenic_scores(
    way: BaseGeometry,
    green: BaseGeometry | None,
    blue: BaseGeometry | None,
    buffer_m: float = 30.0,
) -> tuple[float, float]:
    """Return (green, blue) corridor-overlap fractions in 0..1 for a way."""
    corridor = way.buffer(buffer_m)
    return _overlap_fraction(corridor, green), _overlap_fraction(corridor, blue)
