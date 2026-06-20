"""Known-answer tests for CQI tag-class scoring (scoring.tags).

CQI is a 0..100 cycling suitability value derived from a way's OSM tags.
These tests pin the scoring spine and the acceptance examples from SPEC §6
(busy Hauptstraße scores low; a cycleway scores high; rough surface penalised).
"""

from scoring.tags import cqi_from_tags, highway_base, surface_factor


def test_cycleway_scores_high():
    assert cqi_from_tags({"highway": "cycleway"}) >= 85


def test_quiet_residential_scores_medium():
    cqi = cqi_from_tags({"highway": "residential", "maxspeed": "30"})
    assert 50 <= cqi <= 75


def test_busy_hauptstrasse_scores_low():
    # highway=primary, 50 km/h, no cycle infrastructure -> low suitability
    cqi = cqi_from_tags({"highway": "primary", "maxspeed": "50", "name": "Hauptstraße"})
    assert cqi <= 25


def test_rough_surface_penalised():
    smooth = cqi_from_tags({"highway": "cycleway", "surface": "asphalt"})
    rough = cqi_from_tags({"highway": "cycleway", "surface": "sett"})
    assert rough < smooth


def test_cycle_lane_lifts_a_road():
    plain = cqi_from_tags({"highway": "tertiary", "maxspeed": "50"})
    with_lane = cqi_from_tags({"highway": "tertiary", "maxspeed": "50", "cycleway": "track"})
    assert with_lane > plain


def test_score_clamped_to_0_100():
    for tags in ({"highway": "cycleway"}, {"highway": "primary", "maxspeed": "100"}, {}):
        cqi = cqi_from_tags(tags)
        assert 0.0 <= cqi <= 100.0


def test_highway_base_ranks_classes():
    assert highway_base("cycleway") > highway_base("residential") > highway_base("primary")


def test_surface_factor_penalises_rough():
    assert surface_factor("asphalt") == 1.0
    assert surface_factor("sett") < 1.0
    assert surface_factor(None) == 1.0  # unknown surface assumed rideable
