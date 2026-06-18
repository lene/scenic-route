package scenicroute

import java.nio.file.Paths

class AreaConfigTest extends munit.FunSuite:

  private val fixturePath =
    Paths.get(getClass.getResource("/fixtures/test-area.toml").toURI)

  test("load area config from TOML file"):
    val cfg = AreaConfig.load(fixturePath)
    assertEquals(cfg.id, "test-area")
    assertEquals(cfg.boundaryRelationId, 12345L)
    assertEquals(cfg.bufferKm, 10)
    assertEquals(cfg.pbfUrls, List("https://example.com/test-area.osm.pbf"))
    assertEquals(cfg.pbfFile, "data/test-area.osm.pbf")
    assertEquals(cfg.graphCache, "graph-cache/test-area")
    assertEqualsDouble(cfg.demoStart.lat, 52.5, delta = 1e-9)
    assertEqualsDouble(cfg.demoStart.lon, 13.4, delta = 1e-9)
    assertEqualsDouble(cfg.demoEnd.lat, 52.6, delta = 1e-9)
    assertEqualsDouble(cfg.demoEnd.lon, 13.5, delta = 1e-9)

  test("loaded area config id is non-empty"):
    val cfg = AreaConfig.load(fixturePath)
    assert(cfg.id.nonEmpty)
