#!/usr/bin/env python3
"""Score an area's ways into the global per-way CSV store.

    score_area.py <area.toml>

Reads `paths.pbf_file` and `paths.score_file` from the area config, runs the
scoring pipeline over the clipped extract, and merges the result into the
store (idempotent per way). Heavy/manual — not run in CI (SPEC §9).
"""

import sys
import tomllib
from pathlib import Path

from scoring.pipeline import run


def main(toml_path: str) -> None:
    with Path(toml_path).open("rb") as f:
        cfg = tomllib.load(f)
    paths = cfg["paths"]
    pbf, out = paths["pbf_file"], paths["score_file"]
    print(f"Scoring {pbf} -> {out}")
    total = run(pbf, out)
    print(f"Store now holds {total} ways: {out}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1])
