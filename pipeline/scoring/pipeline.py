"""Scoring pipeline orchestration: ways + features -> per-way ScoreRows.

Wires the pieces (SPEC §5.2): project to a metric CRS, detect sidepaths so each
path inherits its parallel road's context, score CQI/LTS, measure green/blue
corridor overlap, blend, and emit ScoreRows keyed by way id. Idempotent: way
ids already in the store are skipped (SPEC §2).

Heavy I/O (reading the PBF) lives in scoring.io_osm; this module works on
GeoDataFrames so it stays unit-testable on tiny hand-built fixtures.
"""

from collections.abc import Mapping

import geopandas as gpd
from shapely import STRtree, union_all
from shapely.geometry.base import BaseGeometry

from scoring.blend import DEFAULT_WEIGHTS, blend
from scoring.cqi import score
from scoring.scenic import scenic_scores
from scoring.sidepath import Way, detect_sidepaths
from scoring.store import ScoreRow

# Metric CRS for central Europe (matches make_clip_polygon; DECISIONS #8).
METRIC_CRS = "EPSG:25833"

# Separated-path classes that can be sidepaths and get scenic treatment.
_PATH_CLASSES = {"cycleway", "path", "footway"}
# highway values that are not motor roads (excluded as sidepath parents).
_NON_ROAD = _PATH_CLASSES | {"pedestrian", "steps", "construction", "proposed"}


def _is_road(tags: Mapping[str, str]) -> bool:
    hw = tags.get("highway")
    return hw is not None and hw not in _NON_ROAD


def _nearby_union(
    tree: STRtree | None, geoms, corridor: BaseGeometry
) -> BaseGeometry | None:
    """Union of features whose bbox intersects the corridor (precise overlap
    is then measured by scenic_scores). None if the tree is empty / no hit."""
    if tree is None:
        return None
    idx = tree.query(corridor)
    if len(idx) == 0:
        return None
    return union_all([geoms[i] for i in idx])


def score_ways(
    ways_gdf: gpd.GeoDataFrame,
    green_gdf: gpd.GeoDataFrame,
    blue_gdf: gpd.GeoDataFrame,
    *,
    weights: dict[str, float] = DEFAULT_WEIGHTS,
    buffer_m: float = 30.0,
    existing: Mapping[int, ScoreRow] | None = None,
) -> list[ScoreRow]:
    """Score every way not already present in `existing`."""
    existing = existing or {}

    ways_m = ways_gdf.to_crs(METRIC_CRS)
    green_geoms = green_gdf.to_crs(METRIC_CRS).geometry.values if len(green_gdf) else []
    blue_geoms = blue_gdf.to_crs(METRIC_CRS).geometry.values if len(blue_gdf) else []
    green_tree = STRtree(green_geoms) if len(green_geoms) else None
    blue_tree = STRtree(blue_geoms) if len(blue_geoms) else None

    roads = [
        Way(int(r.way_id), r.geometry, r.tags)
        for r in ways_m.itertuples()
        if _is_road(r.tags)
    ]
    paths = [
        Way(int(r.way_id), r.geometry, r.tags)
        for r in ways_m.itertuples()
        if r.tags.get("highway") in _PATH_CLASSES
    ]
    parallels = detect_sidepaths(paths, roads)

    rows: list[ScoreRow] = []
    for r in ways_m.itertuples():
        way_id = int(r.way_id)
        if way_id in existing:
            continue
        cqi, lts = score(r.tags, parallels.get(way_id))
        corridor = r.geometry.buffer(buffer_m)
        green = _nearby_union(green_tree, green_geoms, corridor)
        blue = _nearby_union(blue_tree, blue_geoms, corridor)
        g, b = scenic_scores(r.geometry, green, blue, buffer_m=buffer_m)
        rows.append(ScoreRow(way_id, cqi, lts, g, b, blend(cqi, lts, g, b, weights=weights)))
    return rows


def run(pbf_path: str, out_path: str) -> int:
    """Full build: read PBF, score the ways, merge into the store. Returns the
    number of ways in the resulting store. Heavy — manual/occasional, not CI."""
    from pathlib import Path

    from scoring import io_osm
    from scoring.store import load, save, upsert

    ways_gdf, green_gdf, blue_gdf = io_osm.load(pbf_path)
    existing = load(Path(out_path))
    rows = score_ways(ways_gdf, green_gdf, blue_gdf, existing=existing)
    merged = upsert(existing, rows)
    save(Path(out_path), merged.values())
    return len(merged)
