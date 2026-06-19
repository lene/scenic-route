"""CQI tag-class scoring (pure functions over OSM tag dicts).

Pragmatic adaptation of the OSM Cycling Quality Index by GearKite
(https://github.com/GearKite/OSM-Cycling-Quality-Index), itself derived from
the SupaplexOSM Cycling Quality Index. GPL-3.0. We port the scoring *spine*
(highway class, cycle-infrastructure presence, surface, maxspeed) rather than
the full reference; finer edge tags are out of scope for v1 (DECISIONS #13).

CQI is a 0..100 cycling-suitability value computed from a way's effective
highway class and maxspeed. "Effective" matters for sidepaths: a cycleway
running alongside a main road inherits that road's class/speed (scoring.cqi,
scoring.sidepath) — this module scores whatever (highway, maxspeed) it is given.
"""

# Base suitability by highway class (0..100), before surface/speed modifiers.
_HIGHWAY_BASE: dict[str, float] = {
    "cycleway": 90.0,
    "path": 80.0,
    "track": 70.0,
    "living_street": 75.0,
    "residential": 60.0,
    "service": 55.0,
    "pedestrian": 50.0,
    "unclassified": 50.0,
    "tertiary": 45.0,
    "tertiary_link": 45.0,
    "secondary": 30.0,
    "secondary_link": 30.0,
    "primary": 15.0,
    "primary_link": 15.0,
    "trunk": 5.0,
    "trunk_link": 5.0,
    "footway": 40.0,
}
_DEFAULT_BASE = 40.0

# Highway classes that are inherently separated from motor traffic.
_SEPARATED = {"cycleway", "path", "footway", "pedestrian"}

# Surface comfort multipliers. Unknown surface is assumed rideable (urban
# default is paved); only explicitly rough surfaces are penalised.
_SURFACE_FACTOR: dict[str, float] = {
    "asphalt": 1.0,
    "concrete": 1.0,
    "paved": 1.0,
    "paving_stones": 0.95,
    "concrete:plates": 0.9,
    "compacted": 0.85,
    "fine_gravel": 0.85,
    "gravel": 0.65,
    "unpaved": 0.65,
    "ground": 0.6,
    "dirt": 0.6,
    "grass": 0.5,
    "sand": 0.4,
    "sett": 0.6,
    "cobblestone": 0.5,
    "unhewn_cobblestone": 0.45,
}


def highway_base(highway: str | None) -> float:
    """Base 0..100 suitability for a highway class."""
    if highway is None:
        return _DEFAULT_BASE
    return _HIGHWAY_BASE.get(highway, _DEFAULT_BASE)


def surface_factor(surface: str | None) -> float:
    """Comfort multiplier in (0, 1]; 1.0 for good/unknown surfaces."""
    if surface is None:
        return 1.0
    return _SURFACE_FACTOR.get(surface, 1.0)


def cycle_infrastructure(tags: dict[str, str]) -> str | None:
    """Quality of on-road cycle infrastructure: "track", "lane", or None.

    A `cycleway=track` (or `cycleway:both/left/right=track`) is a separated
    cycle track; `cycleway=lane` is a painted lane. Anything explicitly "no"
    or absent is None.
    """
    keys = ("cycleway", "cycleway:both", "cycleway:left", "cycleway:right")
    values = {tags[k] for k in keys if k in tags}
    if values & {"track", "opposite_track"}:
        return "track"
    if values & {"lane", "opposite_lane"}:
        return "lane"
    return None


def _parse_maxspeed(maxspeed: str | None) -> float | None:
    if maxspeed is None:
        return None
    head = maxspeed.strip().split()[0] if maxspeed.strip() else ""
    try:
        return float(head)
    except ValueError:
        return None  # "walk", "none", etc. — not a numeric limit


def _maxspeed_factor(maxspeed: str | None, separated: bool) -> float:
    """Speed of adjacent motor traffic lowers suitability when not separated."""
    if separated:
        return 1.0
    speed = _parse_maxspeed(maxspeed)
    if speed is None:
        return 1.0
    if speed <= 30:
        return 1.0
    if speed <= 50:
        return 0.85
    if speed <= 70:
        return 0.6
    return 0.4


def cqi_from_tags(tags: dict[str, str]) -> float:
    """CQI 0..100 for a way from its own (effective) tags.

    base(highway) lifted by any cycle infrastructure, then scaled by surface
    comfort and — for non-separated ways — adjacent traffic speed.
    """
    highway = tags.get("highway")
    base = highway_base(highway)

    infra = cycle_infrastructure(tags)
    if infra == "track":
        base = max(base, 85.0)
    elif infra == "lane":
        base = max(base, 65.0)

    separated = highway in _SEPARATED or infra is not None
    cqi = base * surface_factor(tags.get("surface")) * _maxspeed_factor(tags.get("maxspeed"), separated)
    return max(0.0, min(100.0, cqi))
