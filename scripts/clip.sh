#!/usr/bin/env bash
# Build the clipped Berlin+80km extract from the Geofabrik downloads.
# Prerequisites: osmium-tool, python3 with shapely+pyproj.
# Usage: scripts/clip.sh [area-toml]   (default: areas/berlin.toml)
set -euo pipefail

AREA_TOML="${1:-areas/berlin.toml}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Read config values
read -r RELATION_ID < <(python3 -c "
import tomllib
with open('$AREA_TOML', 'rb') as f:
    cfg = tomllib.load(f)
print(cfg['boundary']['relation_id'])
")
read -r PBF_OUT < <(python3 -c "
import tomllib
with open('$AREA_TOML', 'rb') as f:
    cfg = tomllib.load(f)
print(cfg['paths']['pbf_file'])
")
AREA_ID=$(python3 -c "
import tomllib
with open('$AREA_TOML', 'rb') as f:
    cfg = tomllib.load(f)
print(cfg['area']['id'])
")

POLY_FILE="areas/${AREA_ID}-80km.poly"
BOUNDARY_PBF="data/${AREA_ID}-boundary.osm.pbf"
BOUNDARY_GEOJSON="data/${AREA_ID}-boundary.geojson"
mkdir -p data graph-cache

echo "=== Step 1: extract boundary relation $RELATION_ID from Berlin extract ==="
osmium tags-filter \
    data/berlin-latest.osm.pbf \
    r/admin_level=4 \
    -o "$BOUNDARY_PBF" --overwrite

echo "=== Step 2: convert boundary to GeoJSON ==="
osmium export "$BOUNDARY_PBF" -f geojson -o "$BOUNDARY_GEOJSON" --overwrite

echo "=== Step 3: generate $POLY_FILE ==="
# Pass paths as env vars so the heredoc can use single-quotes (safe for Python f-strings).
GEOJSON_PATH="$BOUNDARY_GEOJSON" TOML_PATH="$AREA_TOML" OUT_PATH="$POLY_FILE" \
python3 - <<'PYEOF'
import json, os, sys
from pathlib import Path
sys.path.insert(0, str(Path("pipeline")))
from make_clip_polygon import write_poly_file

geojson_path = Path(os.environ["GEOJSON_PATH"])
toml_path    = Path(os.environ["TOML_PATH"])
out_path     = Path(os.environ["OUT_PATH"])

with geojson_path.open() as fh:
    fc = json.load(fh)

# Find the first relation-level multipolygon outer ring
coords = None
for feat in fc.get("features", []):
    geom = feat.get("geometry", {})
    if geom.get("type") == "MultiPolygon":
        coords = geom["coordinates"][0][0]
        break
    elif geom.get("type") == "Polygon":
        coords = geom["coordinates"][0]
        break

if coords is None:
    print("ERROR: no polygon/multipolygon found in boundary GeoJSON", file=sys.stderr)
    sys.exit(1)

write_poly_file(toml_path, coords, out_path)
print(f"Wrote {out_path}")
PYEOF

MERGED_PBF="data/${AREA_ID}-merged.osm.pbf"

echo "=== Step 4a: merge Berlin + Brandenburg ==="
osmium merge \
    data/berlin-latest.osm.pbf data/brandenburg-latest.osm.pbf \
    -o "$MERGED_PBF" --overwrite

echo "=== Step 4b: clip merged extract to polygon ==="
osmium extract \
    --polygon "$POLY_FILE" \
    "$MERGED_PBF" \
    -o "$PBF_OUT" --overwrite --strategy=smart

echo "=== Done: $PBF_OUT ==="
