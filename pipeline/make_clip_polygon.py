"""Generate an osmium-compatible .poly clip polygon from a boundary ring.

The input ring (list of (lon, lat) WGS84 coordinate pairs) is buffered by
`buffer_km` kilometres in a metric projection (EPSG:25833 — ETRS89/UTM zone 33N,
appropriate for central Europe) then converted back to WGS84. The output is
an osmium/JOSM .poly-format string.
"""

from __future__ import annotations

import sys
import tomllib
from pathlib import Path
from typing import Sequence

from pyproj import Transformer
from shapely.geometry import Polygon
from shapely.ops import transform

_TO_METRIC = Transformer.from_crs("EPSG:4326", "EPSG:25833", always_xy=True)
_TO_WGS84 = Transformer.from_crs("EPSG:25833", "EPSG:4326", always_xy=True)


def buffer_ring_wgs84(
    ring: Sequence[tuple[float, float]],
    buffer_km: float,
) -> Polygon:
    """Buffer a WGS84 (lon, lat) ring by `buffer_km` km; return WGS84 Polygon."""
    poly_metric = transform(_TO_METRIC.transform, Polygon(ring))
    buffered_metric = poly_metric.buffer(buffer_km * 1000)
    return transform(_TO_WGS84.transform, buffered_metric)


def ring_to_poly_lines(name: str, polygon: Polygon) -> list[str]:
    """Encode a WGS84 Polygon as osmium .poly format lines (no trailing newline)."""
    coords = list(polygon.exterior.coords)
    lines = [name, "1"]
    for lon, lat in coords:
        lines.append(f"   {lon:.7f}   {lat:.7f}")
    lines.append("END")
    return lines


def _load_area_config(toml_path: Path) -> tuple[str, int, float]:
    """Return (area_id, boundary_relation_id, buffer_km) from an area TOML."""
    with toml_path.open("rb") as fh:
        cfg = tomllib.load(fh)
    area_id = cfg["area"]["id"]
    buffer_km = float(cfg["boundary"]["buffer_km"])
    return area_id, buffer_km


def write_poly_file(toml_path: Path, ring: Sequence[tuple[float, float]], out_path: Path) -> None:
    """Buffer boundary ring from config and write osmium .poly file."""
    area_id, buffer_km = _load_area_config(toml_path)
    buffered = buffer_ring_wgs84(ring, buffer_km)
    lines = ring_to_poly_lines(area_id, buffered)
    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    # ponytail: minimal CLI — enough to drive scripts/clip.sh; not a full argparse
    import json

    if len(sys.argv) != 4:
        print(f"Usage: {sys.argv[0]} <area.toml> <ring.geojson> <out.poly>", file=sys.stderr)
        sys.exit(1)
    toml_path = Path(sys.argv[1])
    ring_path = Path(sys.argv[2])
    out_path = Path(sys.argv[3])
    geojson = json.loads(ring_path.read_text())
    coords = geojson["coordinates"][0]  # outer ring of a Polygon GeoJSON
    write_poly_file(toml_path, coords, out_path)
    print(f"Wrote {out_path}")
