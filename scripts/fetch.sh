#!/usr/bin/env bash
# Download Geofabrik extracts for the Berlin area.
# Usage: scripts/fetch.sh [area-toml]   (default: areas/berlin.toml)
# Output files land in data/ (gitignored).
set -euo pipefail

AREA_TOML="${1:-areas/berlin.toml}"
mkdir -p data

python3 -c "
import tomllib, sys
with open('$AREA_TOML', 'rb') as f:
    cfg = tomllib.load(f)
for url in cfg['sources']['pbf_urls']:
    print(url)
" | while IFS= read -r url; do
    fname="data/$(basename "$url")"
    if [[ -f "$fname" ]]; then
        echo "Already present: $fname"
    else
        echo "Fetching $url → $fname"
        curl -L --progress-bar -o "$fname" "$url"
    fi
done

echo "Fetch complete."
