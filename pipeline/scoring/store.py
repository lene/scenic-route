"""CSV score store keyed by OSM way id (SPEC §2, DECISIONS #15).

Global and area-independent: one entry per distinct way, idempotent per way —
a way an existing store already covers is never re-scored. CSV keeps the
handoff human-inspectable at CHECKPOINT 1 and trivial to read from the JVM
in Phase 2.
"""

import csv
from collections.abc import Iterable
from dataclasses import astuple, dataclass
from pathlib import Path

FIELDS = ["way_id", "cqi", "lts", "green", "blue", "score"]


@dataclass(frozen=True)
class ScoreRow:
    way_id: int
    cqi: float
    lts: int
    green: float
    blue: float
    score: float


def load(path: Path) -> dict[int, ScoreRow]:
    """Existing rows keyed by way_id; empty dict if the file is absent."""
    path = Path(path)
    if not path.exists():
        return {}
    rows: dict[int, ScoreRow] = {}
    with path.open(newline="") as f:
        for r in csv.DictReader(f):
            row = ScoreRow(
                way_id=int(r["way_id"]),
                cqi=float(r["cqi"]),
                lts=int(r["lts"]),
                green=float(r["green"]),
                blue=float(r["blue"]),
                score=float(r["score"]),
            )
            rows[row.way_id] = row
    return rows


def save(path: Path, rows: Iterable[ScoreRow]) -> None:
    """Write rows as CSV, one per way_id, sorted by way_id."""
    deduped = {r.way_id: r for r in rows}
    with Path(path).open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(FIELDS)
        for way_id in sorted(deduped):
            w.writerow(astuple(deduped[way_id]))


def upsert(existing: dict[int, ScoreRow], new: Iterable[ScoreRow]) -> dict[int, ScoreRow]:
    """Merge new rows into existing, keeping existing (idempotent per way)."""
    merged = dict(existing)
    for row in new:
        merged.setdefault(row.way_id, row)
    return merged
