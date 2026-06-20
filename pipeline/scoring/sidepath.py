"""Geometric sidepath detection (SPEC §2).

A separated path (cycleway/footway/path) is a *sidepath* of a road when most of
it runs close alongside that road. We sample check-points along the path and, via
an STRtree over the road geometries, find the nearest road within a distance
threshold for each. If at least 2/3 of the check-points hug roads, the path is a
sidepath of the road they most consistently hug, and it inherits that road's
highway class + maxspeed (scoring.cqi.Parallel).

Geometries must be in a metric CRS (metres); the pipeline projects to EPSG:25833
before calling.
"""

from collections import Counter
from dataclasses import dataclass

from shapely import STRtree
from shapely.geometry.base import BaseGeometry

from scoring.cqi import Parallel


@dataclass(frozen=True)
class Way:
    way_id: int
    geom: BaseGeometry
    tags: dict[str, str]


def _sample_points(line: BaseGeometry, step_m: float, min_points: int = 3) -> list[BaseGeometry]:
    length = line.length
    if length == 0.0:
        return [line.interpolate(0.0)]
    n = max(min_points, int(length // step_m) + 1)
    return [line.interpolate(i / (n - 1), normalized=True) for i in range(n)]


def detect_sidepaths(
    paths: list[Way],
    roads: list[Way],
    *,
    sample_step_m: float = 10.0,
    max_dist_m: float = 25.0,
    threshold: float = 2.0 / 3.0,
) -> dict[int, Parallel]:
    """Map way_id -> Parallel for each path that is a sidepath of some road."""
    result: dict[int, Parallel] = {}
    if not roads:
        return result
    tree = STRtree([r.geom for r in roads])
    for path in paths:
        points = _sample_points(path.geom, sample_step_m)
        if not points:
            continue
        nearest: list[int] = []
        for pt in points:
            idx = tree.query_nearest(pt, max_distance=max_dist_m)
            if len(idx) > 0:
                nearest.append(int(idx[0]))
        if nearest and len(nearest) / len(points) >= threshold:
            dominant = Counter(nearest).most_common(1)[0][0]
            road = roads[dominant]
            result[path.way_id] = Parallel(
                highway=road.tags.get("highway"),
                maxspeed=road.tags.get("maxspeed"),
            )
    return result
