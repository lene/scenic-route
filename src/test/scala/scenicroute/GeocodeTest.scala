package scenicroute

class GeocodeTest extends munit.FunSuite:

  test("parseNominatim extracts label, lat, lon (Nominatim lat/lon are strings)"):
    val body =
      """[{"display_name":"Brandenburger Tor, Berlin","lat":"52.5162746","lon":"13.3777041"}]"""
    assertEquals(
      Geocode.parseNominatim(body),
      List(GeoResult("Brandenburger Tor, Berlin", 52.5162746, 13.3777041))
    )

  test("parseNominatim keeps well-formed entries and drops malformed ones"):
    val body =
      """[{"display_name":"ok","lat":"1.0","lon":"2.0"},{"display_name":"bad","lat":"x","lon":"2"}]"""
    assertEquals(Geocode.parseNominatim(body).size, 1)

  test("parseNominatim returns Nil on a non-array payload"):
    assertEquals(Geocode.parseNominatim("{}"), Nil)
