"""Known-answer tests for green/blue feature predicates (scoring.features).

Tag sets are fixed by SPEC §5.2. These classify a feature's tags as green
(parks/forest/grass/...), blue (water), or neither.
"""

from scoring.features import is_blue, is_green


def test_park_is_green():
    assert is_green({"leisure": "park"})
    assert not is_blue({"leisure": "park"})


def test_forest_landuse_is_green():
    assert is_green({"landuse": "forest"})


def test_wood_natural_is_green():
    assert is_green({"natural": "wood"})


def test_protected_area_is_green():
    assert is_green({"boundary": "protected_area"})


def test_water_is_blue():
    assert is_blue({"natural": "water"})
    assert not is_green({"natural": "water"})


def test_river_is_blue():
    assert is_blue({"waterway": "river"})


def test_building_is_neither():
    tags = {"building": "yes"}
    assert not is_green(tags)
    assert not is_blue(tags)
