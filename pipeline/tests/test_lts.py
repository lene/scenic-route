"""Known-answer tests for Level of Traffic Stress (scoring.tags.lts_from_tags).

LTS is a 1..4 integer: 1 = comfortable for all ages/abilities (separated or
very quiet), 4 = high-stress (fast, busy, no cycle provision). Coarse mapping
per DECISIONS #13 — not the full reference matrix.
"""

from scoring.tags import lts_from_tags


def test_separated_cycleway_is_lts1():
    assert lts_from_tags({"highway": "cycleway"}) == 1


def test_quiet_residential_is_lts1():
    assert lts_from_tags({"highway": "residential", "maxspeed": "30"}) == 1


def test_faster_residential_is_lts2():
    assert lts_from_tags({"highway": "residential", "maxspeed": "50"}) == 2


def test_cycle_track_on_busy_road_is_lts1():
    assert lts_from_tags({"highway": "primary", "maxspeed": "50", "cycleway": "track"}) == 1


def test_cycle_lane_on_moderate_road_is_lts2():
    assert lts_from_tags({"highway": "tertiary", "maxspeed": "50", "cycleway": "lane"}) == 2


def test_busy_primary_no_infra_is_lts4():
    assert lts_from_tags({"highway": "primary", "maxspeed": "50"}) == 4


def test_lts_always_in_range():
    for tags in (
        {"highway": "cycleway"},
        {"highway": "trunk", "maxspeed": "100"},
        {"highway": "secondary"},
        {},
    ):
        assert lts_from_tags(tags) in (1, 2, 3, 4)
