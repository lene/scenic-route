"""Known-answer tests for the CSV way-ID score store (scoring.store).

The store is the global, area-independent handoff (SPEC §2): keyed by OSM way
id, idempotent per way (a way an existing area already covered is never
re-scored). CSV so it is human-inspectable at CHECKPOINT 1 (DECISIONS #15).
"""

from scoring.store import FIELDS, ScoreRow, load, save, upsert


def test_save_then_load_round_trips(tmp_path):
    path = tmp_path / "scores.csv"
    rows = [
        ScoreRow(way_id=42, cqi=90.0, lts=1, green=0.5, blue=0.25, score=0.8),
        ScoreRow(way_id=7, cqi=12.5, lts=4, green=0.0, blue=0.0, score=0.1),
    ]
    save(path, rows)
    loaded = load(path)
    assert loaded[42] == rows[0]
    assert loaded[7] == rows[1]
    assert isinstance(loaded[42].way_id, int)
    assert isinstance(loaded[42].lts, int)
    assert isinstance(loaded[42].green, float)


def test_load_missing_file_is_empty(tmp_path):
    assert load(tmp_path / "absent.csv") == {}


def test_header_is_the_five_field_schema(tmp_path):
    path = tmp_path / "scores.csv"
    save(path, [ScoreRow(1, 50.0, 2, 0.3, 0.3, 0.5)])
    header = path.read_text().splitlines()[0]
    assert header.split(",") == FIELDS
    assert FIELDS == ["way_id", "cqi", "lts", "green", "blue", "score"]


def test_upsert_adds_new_way_ids():
    existing = {1: ScoreRow(1, 50.0, 2, 0.3, 0.3, 0.5)}
    merged = upsert(existing, [ScoreRow(2, 90.0, 1, 0.9, 0.1, 0.8)])
    assert set(merged) == {1, 2}


def test_upsert_is_idempotent_per_way():
    # A way an existing store already covers is NOT re-scored (SPEC §2).
    original = ScoreRow(1, 50.0, 2, 0.3, 0.3, 0.5)
    rescored = ScoreRow(1, 99.0, 1, 0.9, 0.9, 0.99)
    merged = upsert({1: original}, [rescored])
    assert merged[1] == original


def test_save_dedupes_one_row_per_way(tmp_path):
    path = tmp_path / "scores.csv"
    save(path, [ScoreRow(1, 50.0, 2, 0.3, 0.3, 0.5)])
    loaded = load(path)
    body = [ln for ln in path.read_text().splitlines()[1:] if ln]
    assert len(body) == 1
    assert set(loaded) == {1}
