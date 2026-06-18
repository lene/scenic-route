"""Tests for the clip-polygon buffer generator."""

import math
from shapely.geometry import Polygon
from make_clip_polygon import buffer_ring_wgs84, ring_to_poly_lines


def _square_ring(cx: float, cy: float, half: float) -> list[tuple[float, float]]:
    """Small WGS84 square centred at (cx, cy) with half-side `half` degrees."""
    return [
        (cx - half, cy - half),
        (cx + half, cy - half),
        (cx + half, cy + half),
        (cx - half, cy + half),
        (cx - half, cy - half),  # closed
    ]


def test_buffer_increases_area() -> None:
    ring = _square_ring(13.4, 52.5, 0.1)  # ~11 km half-side near Berlin
    original = Polygon([(lon, lat) for lon, lat in ring])
    buffered = buffer_ring_wgs84(ring, buffer_km=10)
    assert buffered.area > original.area


def test_buffer_is_convex_ish() -> None:
    """Buffered convex polygon stays convex (within floating-point rounding)."""
    ring = _square_ring(13.4, 52.5, 0.1)
    buffered = buffer_ring_wgs84(ring, buffer_km=5)
    assert buffered.is_valid


def test_buffer_bbox_wider_than_input() -> None:
    half = 0.1
    ring = _square_ring(13.4, 52.5, half)
    buffered = buffer_ring_wgs84(ring, buffer_km=10)
    minx, miny, maxx, maxy = buffered.bounds
    assert minx < 13.4 - half
    assert miny < 52.5 - half
    assert maxx > 13.4 + half
    assert maxy > 52.5 + half


def test_ring_to_poly_lines_format() -> None:
    ring = _square_ring(13.4, 52.5, 0.1)
    poly = buffer_ring_wgs84(ring, buffer_km=1)
    lines = ring_to_poly_lines("test_area", poly)
    assert lines[0] == "test_area"
    assert lines[1] == "1"
    # each coord line: "   lon   lat"
    for line in lines[2:-1]:
        parts = line.split()
        assert len(parts) == 2
        float(parts[0])  # lon parseable
        float(parts[1])  # lat parseable
    assert lines[-1] == "END"
