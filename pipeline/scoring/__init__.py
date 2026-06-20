"""Offline per-way scoring pipeline for scenic-route (Phase 1).

Produces a flat file keyed by OSM way ID:
    way_id -> {cqi: 0..100, lts: 1..4, green: 0..1, blue: 0..1, score: 0..1}

The store is global and area-independent (SPEC §2): scored once over the union
of ways covered, idempotent per way. Consumed in Phase 2 by a GraphHopper
TagParser that looks each way's score up during import.
"""
