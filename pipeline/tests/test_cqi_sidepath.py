"""The SPEC §9 mandated cycleway-plus-parallel-road fixture.

CQI input is multi-way: a cycleway running alongside a main road is a
*sidepath* and its quality is judged in the context of that road, not as an
isolated ideal cycleway (SPEC §2). These tests pin (a) geometric sidepath
detection and (b) the score inheriting the parallel road's class/speed.

Geometries are in a metric CRS (metres) — the pipeline projects to EPSG:25833
before calling.
"""

from shapely.geometry import LineString

from scoring.cqi import Parallel, score
from scoring.sidepath import Way, detect_sidepaths
from scoring.tags import cqi_from_tags

# A primary road and a cycleway 5 m to its north, both running x = 0..100.
ROAD = Way(
    way_id=1,
    geom=LineString([(x, 0.0) for x in range(0, 101, 10)]),
    tags={"highway": "primary", "maxspeed": "50", "name": "Hauptstraße"},
)
SIDEPATH = Way(
    way_id=2,
    geom=LineString([(x, 5.0) for x in range(0, 101, 10)]),
    tags={"highway": "cycleway"},
)


def test_parallel_cycleway_is_detected_as_sidepath():
    found = detect_sidepaths([SIDEPATH], [ROAD], max_dist_m=25.0)
    assert SIDEPATH.way_id in found


def test_sidepath_inherits_parallel_road_class_and_speed():
    found = detect_sidepaths([SIDEPATH], [ROAD], max_dist_m=25.0)
    parallel = found[SIDEPATH.way_id]
    assert parallel.highway == "primary"
    assert parallel.maxspeed == "50"


def test_independent_cycleway_is_not_a_sidepath():
    far_road = Way(way_id=1, geom=LineString([(0, 1000), (100, 1000)]), tags=ROAD.tags)
    found = detect_sidepaths([SIDEPATH], [far_road], max_dist_m=25.0)
    assert SIDEPATH.way_id not in found


def test_perpendicular_crossing_is_not_a_sidepath():
    crossing = Way(
        way_id=3,
        geom=LineString([(50, -50), (50, 50)]),
        tags={"highway": "cycleway"},
    )
    found = detect_sidepaths([crossing], [ROAD], max_dist_m=25.0)
    assert crossing.way_id not in found


def test_sidepath_score_is_below_isolated_cycleway():
    isolated_cqi, _ = score(SIDEPATH.tags)
    assert isolated_cqi == cqi_from_tags(SIDEPATH.tags)  # no parallel -> own score
    parallel = Parallel(highway="primary", maxspeed="50")
    sidepath_cqi, sidepath_lts = score(SIDEPATH.tags, parallel)
    assert sidepath_cqi < isolated_cqi          # context of a busy road lowers it
    assert sidepath_cqi > 0
    assert sidepath_lts == 1                     # still physically separated


def test_score_without_parallel_matches_tag_scoring():
    cqi, lts = score({"highway": "residential", "maxspeed": "30"})
    assert cqi == cqi_from_tags({"highway": "residential", "maxspeed": "30"})
    assert lts == 1
