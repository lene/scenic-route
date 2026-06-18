.PHONY: all data build test test-scala test-python clean help

AREA ?= areas/berlin.toml

## Fetch OSM extracts from Geofabrik (run once; idempotent)
data/fetch:
	scripts/fetch.sh $(AREA)
	@touch $@

## Clip to area polygon (requires osmium-tool)
data/clip: data/fetch
	scripts/clip.sh $(AREA)
	@touch $@

## Full data pipeline: fetch + clip
data: data/clip

## Compile Scala project
build:
	sbt compile

## Run all tests
test: test-scala test-python

## Run Scala tests (compile + lint + munit)
test-scala:
	sbt scalafmtCheckAll "scalafixAll --check" test

## Run Python pipeline tests
test-python:
	cd pipeline ; .venv/bin/python -m pytest tests/ -v

## Remove generated artefacts (keeps data/ — delete manually if needed)
clean:
	sbt clean

help:
	@grep -E '^##' Makefile | sed 's/^## //'
