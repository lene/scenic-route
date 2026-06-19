"""Smoke test for the scoring pipeline wiring (scoring.pipeline.score_ways).

Wires sidepath -> cqi -> scenic -> blend over a tiny hand-built set of ways and
features (NOT the Berlin PBF). Asserts the chain produces one valid ScoreRow per
way, that a way through a park gets a green bonus, and that the store-style
idempotency skip works. Geometries are WGS84 (EPSG:4326) near Berlin; the
pipeline projects to metric internally.
"""

import geopandas as gpd
from shapely.geometry import LineString, box

from scoring.pipeline import score_ways
from scoring.store import ScoreRow

# ~5 m north at lat 52.5 is ~4.5e-5 deg latitude.
_WAYS = gpd.GeoDataFrame(
    {
        "way_id": [1, 2, 3],
        "tags": [
            {"highway": "cycleway"},                          # through a park
            {"highway": "primary", "maxspeed": "50"},         # busy road
            {"highway": "cycleway"},                          # plain, no features
        ],
        "geometry": [
            LineString([(13.400, 52.500), (13.402, 52.500)]),
            LineString([(13.410, 52.500), (13.412, 52.500)]),
            LineString([(13.420, 52.500), (13.422, 52.500)]),
        ],
    },
    crs="EPSG:4326",
)
_GREEN = gpd.GeoDataFrame(
    {"geometry": [box(13.399, 52.4995, 13.403, 52.5005)]}, crs="EPSG:4326"
)
_BLUE = gpd.GeoDataFrame({"geometry": [box(13.50, 52.60, 13.51, 52.61)]}, crs="EPSG:4326")


def test_one_row_per_way():
    rows = score_ways(_WAYS, _GREEN, _BLUE)
    assert {r.way_id for r in rows} == {1, 2, 3}
    assert all(isinstance(r, ScoreRow) for r in rows)


def test_values_in_valid_ranges():
    for r in score_ways(_WAYS, _GREEN, _BLUE):
        assert 0.0 <= r.cqi <= 100.0
        assert r.lts in (1, 2, 3, 4)
        assert 0.0 <= r.green <= 1.0
        assert 0.0 <= r.blue <= 1.0
        assert 0.0 <= r.score <= 1.0


def test_way_through_park_gets_green_bonus():
    by_id = {r.way_id: r for r in score_ways(_WAYS, _GREEN, _BLUE)}
    assert by_id[1].green > 0.5     # cycleway inside the park polygon
    assert by_id[3].green == 0.0    # cycleway with no nearby green


def test_existing_way_ids_are_skipped():
    existing = {1: ScoreRow(1, 0, 1, 0, 0, 0)}
    rows = score_ways(_WAYS, _GREEN, _BLUE, existing=existing)
    assert {r.way_id for r in rows} == {2, 3}
