"""Known-answer tests for buffer-overlap scenic scoring (scoring.scenic).

scenic_scores buffers a way into a corridor and measures the fraction of that
corridor overlapping green / blue features -> two values in 0..1. Geometries
are in a metric CRS (metres); the pipeline projects to EPSG:25833 before
calling, so these tests build geometries directly in a metre plane.
"""

from shapely.geometry import LineString, Polygon

from scoring.scenic import scenic_scores


def _box(x0, y0, x1, y1):
    return Polygon([(x0, y0), (x1, y0), (x1, y1), (x0, y1)])


def test_way_through_green_scores_high():
    way = LineString([(0, 0), (100, 0)])
    green = _box(-50, -50, 150, 50)  # way runs through the middle of the park
    g, b = scenic_scores(way, green=green, blue=None, buffer_m=20.0)
    assert g > 0.9
    assert b == 0.0


def test_way_disjoint_from_green_scores_zero():
    way = LineString([(0, 0), (100, 0)])
    green = _box(1000, 1000, 1100, 1100)  # far away
    g, _ = scenic_scores(way, green=green, blue=None, buffer_m=20.0)
    assert g == 0.0


def test_adjacent_green_within_buffer_scores_partial():
    way = LineString([(0, 0), (100, 0)])
    # park edge starts 5 m north of the way; corridor (20 m) catches a strip.
    green = _box(-50, 5, 150, 200)
    g, _ = scenic_scores(way, green=green, blue=None, buffer_m=20.0)
    assert 0.0 < g < 1.0


def test_more_overlap_scores_higher():
    way = LineString([(0, 0), (100, 0)])
    small = _box(0, -20, 30, 20)
    big = _box(0, -20, 90, 20)
    g_small, _ = scenic_scores(way, green=small, blue=None, buffer_m=20.0)
    g_big, _ = scenic_scores(way, green=big, blue=None, buffer_m=20.0)
    assert g_big > g_small


def test_green_and_blue_are_independent():
    way = LineString([(0, 0), (100, 0)])
    green = _box(-50, -50, 150, 50)
    blue = _box(1000, 1000, 1100, 1100)
    g, b = scenic_scores(way, green=green, blue=blue, buffer_m=20.0)
    assert g > 0.9
    assert b == 0.0


def test_no_features_scores_zero():
    way = LineString([(0, 0), (100, 0)])
    g, b = scenic_scores(way, green=None, blue=None, buffer_m=20.0)
    assert (g, b) == (0.0, 0.0)
