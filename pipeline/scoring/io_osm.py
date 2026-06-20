"""Read an OSM PBF into the GeoDataFrames the pipeline scores (DECISIONS #14).

The only PBF-touching module. `pyrosm` is imported lazily inside load() so the
pure-logic scoring modules (and their tests) never require the geo stack to be
installed (CI safety — SPEC §9). Verified against the real Berlin+80km extract
at the build step, not unit-tested: it is thin glue over a third-party reader.

Produces:
  ways_gdf  : columns way_id (int), tags (dict), geometry  (cycling network)
  green_gdf : geometry of green features (SPEC §5.2 predicates)
  blue_gdf  : geometry of water features
"""

import geopandas as gpd

from scoring.features import is_blue, is_green

# Tag columns pyrosm may promote out of the `tags` dict; we fold them back in so
# the scoring layer always sees one complete tag dict per way.
_WAY_TAG_COLS = (
    "highway", "surface", "maxspeed", "name", "bicycle", "segregated", "tracktype",
    "cycleway", "cycleway:both", "cycleway:left", "cycleway:right",
)
_FEATURE_TAG_COLS = ("leisure", "landuse", "natural", "boundary", "waterway")

# Union of green + blue tags, as a pyrosm custom_filter (keep matching features).
_FEATURE_FILTER = {
    "leisure": ["park", "garden", "nature_reserve", "recreation_ground"],
    "landuse": ["forest", "meadow", "grass", "village_green", "allotments"],
    "natural": ["wood", "grassland", "heath", "scrub", "water"],
    "boundary": ["national_park", "protected_area"],
    "waterway": ["river", "stream", "canal"],
}


def _row_tags(row, columns: tuple[str, ...]) -> dict[str, str]:
    """Assemble a full tag dict from promoted columns + pyrosm's `tags` dict."""
    tags: dict[str, str] = {}
    extra = row.get("tags")
    if isinstance(extra, dict):
        tags.update({k: v for k, v in extra.items() if v is not None})
    for col in columns:
        val = row.get(col)
        if val is not None and val == val:  # not NaN
            tags[col] = str(val)
    return tags


def load(pbf_path: str) -> tuple[gpd.GeoDataFrame, gpd.GeoDataFrame, gpd.GeoDataFrame]:
    from pyrosm import OSM

    osm = OSM(pbf_path)

    network = osm.get_network(network_type="cycling")
    if network is None or network.empty:
        ways = gpd.GeoDataFrame({"way_id": [], "tags": [], "geometry": []}, crs="EPSG:4326")
    else:
        ways = gpd.GeoDataFrame(
            {
                "way_id": network["id"].astype("int64"),
                "tags": [_row_tags(r, _WAY_TAG_COLS) for _, r in network.iterrows()],
                "geometry": network.geometry,
            },
            crs=network.crs,
        )

    features = osm.get_data_by_custom_criteria(
        custom_filter=_FEATURE_FILTER,
        filter_type="keep",
        keep_nodes=False,
        keep_ways=True,
        keep_relations=True,
    )
    if features is None or features.empty:
        green = blue = gpd.GeoDataFrame({"geometry": []}, crs="EPSG:4326")
    else:
        tags = [_row_tags(r, _FEATURE_TAG_COLS) for _, r in features.iterrows()]
        green_mask = [is_green(t) for t in tags]
        blue_mask = [is_blue(t) for t in tags]
        green = features.loc[green_mask, ["geometry"]].reset_index(drop=True)
        blue = features.loc[blue_mask, ["geometry"]].reset_index(drop=True)

    return ways, green, blue
