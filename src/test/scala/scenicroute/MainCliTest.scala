package scenicroute

class MainCliTest extends munit.FunSuite:

  // ── parseLatLon ────────────────────────────────────────────────────────────

  test("parseLatLon parses a lat,lon pair"):
    assertEquals(Main.parseLatLon("52.51,13.37"), Some(LatLon(52.51, 13.37)))

  test("parseLatLon tolerates whitespace after the comma"):
    assertEquals(Main.parseLatLon("52.51, 13.37"), Some(LatLon(52.51, 13.37)))

  test("parseLatLon rejects a missing longitude"):
    assertEquals(Main.parseLatLon("52.51"), None)

  test("parseLatLon rejects non-numeric input"):
    assertEquals(Main.parseLatLon("here,there"), None)

  test("parseLatLon rejects an out-of-range latitude"):
    assertEquals(Main.parseLatLon("99.9,13.37"), None)

  // ── parseArgs ──────────────────────────────────────────────────────────────

  test("parseArgs parses a full routing request"):
    val got = Main.parseArgs(Array("areas/berlin.toml", "52.51,13.37", "52.42,13.65", "30"))
    assertEquals(got, Some(RouteRequest("areas/berlin.toml", LatLon(52.51, 13.37), LatLon(52.42, 13.65), 30.0)))

  test("parseArgs rejects the wrong number of arguments"):
    assertEquals(Main.parseArgs(Array("only", "two")), None)

  test("parseArgs rejects a non-positive target distance"):
    assertEquals(Main.parseArgs(Array("t.toml", "52.51,13.37", "52.42,13.65", "0")), None)
