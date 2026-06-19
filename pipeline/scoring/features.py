"""Green/blue feature classification from OSM tags (SPEC §5.2).

Pure predicates over tag dicts; the GeoDataFrame filtering that uses them lives
in scoring.io_osm (the only PBF-touching module). Keeping the predicates here
and pure makes them known-answer testable without any spatial data.
"""

# leisure / landuse / natural / boundary values that count as "green".
_GREEN: dict[str, set[str]] = {
    "leisure": {"park", "garden", "nature_reserve", "recreation_ground"},
    "landuse": {"forest", "meadow", "grass", "village_green", "allotments"},
    "natural": {"wood", "grassland", "heath", "scrub"},
    "boundary": {"national_park", "protected_area"},
}

# tags that count as "blue" (water).
_BLUE: dict[str, set[str]] = {
    "natural": {"water"},
    "waterway": {"river", "stream", "canal"},
}


def _matches(tags: dict[str, str], table: dict[str, set[str]]) -> bool:
    return any(tags.get(key) in values for key, values in table.items())


def is_green(tags: dict[str, str]) -> bool:
    return _matches(tags, _GREEN)


def is_blue(tags: dict[str, str]) -> bool:
    return _matches(tags, _BLUE)
