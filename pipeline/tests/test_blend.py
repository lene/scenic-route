"""Known-answer tests for the score blend (scoring.blend).

blend() folds CQI, LTS, green and blue into a single 0..1 routing score.
Default weights make the infrastructure side (cqi+lts) roughly equal to the
scenic side (green+blue) per SPEC §7.2. A gradient term exists at weight 0 as
a seam for v2 elevation (SPEC §7.5) — present but contributing nothing in v1.
"""

from scoring.blend import DEFAULT_WEIGHTS, blend


def test_all_high_inputs_score_high():
    assert blend(cqi=100, lts=1, green=1.0, blue=1.0) == 1.0


def test_all_low_inputs_score_zero():
    assert blend(cqi=0, lts=4, green=0.0, blue=0.0) == 0.0


def test_low_cqi_high_scenic_is_mid():
    # A scenic but low-infrastructure way lands in the middle, not the top.
    score = blend(cqi=0, lts=4, green=1.0, blue=1.0)
    assert 0.4 <= score <= 0.6


def test_score_in_unit_range():
    assert 0.0 <= blend(cqi=37, lts=3, green=0.2, blue=0.8) <= 1.0


def test_gradient_is_zero_weighted_seam():
    base = blend(cqi=50, lts=2, green=0.5, blue=0.5, gradient=0.0)
    steep = blend(cqi=50, lts=2, green=0.5, blue=0.5, gradient=1.0)
    assert base == steep  # gradient weight 0 -> no effect in v1
    assert "gradient" in DEFAULT_WEIGHTS  # seam present
    assert DEFAULT_WEIGHTS["gradient"] == 0.0


def test_cqi_and_scenic_weights_are_balanced():
    infra = DEFAULT_WEIGHTS["cqi"] + DEFAULT_WEIGHTS["lts"]
    scenic = DEFAULT_WEIGHTS["green"] + DEFAULT_WEIGHTS["blue"]
    assert abs(infra - scenic) < 1e-9


def test_custom_weights_are_normalised():
    # Weights need not sum to 1; the blend normalises, so output stays in 0..1.
    w = {"cqi": 2.0, "lts": 2.0, "green": 2.0, "blue": 2.0, "gradient": 0.0}
    assert blend(cqi=100, lts=1, green=1.0, blue=1.0, weights=w) == 1.0
