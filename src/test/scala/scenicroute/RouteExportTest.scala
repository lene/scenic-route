package scenicroute

import com.graphhopper.util.details.PathDetail
import java.util.Locale

class RouteExportTest extends munit.FunSuite:

  // Minimal Route builder — stubs PathDetail to return fixed EV values (see RouteSelectionTest).
  private def mkRoute(distM: Double, points: List[LatLon], cqi: Double, scenic: Double): Route =
    val cqiDetail = new PathDetail(0):
      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      override def getValue: AnyRef = Double.box(cqi)
      override def getLength: Int   = 1
    val scenicDetail = new PathDetail(0):
      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      override def getValue: AnyRef = Double.box(scenic)
      override def getLength: Int   = 1
    Route(distM, points, List(cqiDetail), List(scenicDetail))

  private def mkRanked(distM: Double, points: List[LatLon], cqi: Double, scenic: Double, score: Double): RankedRoute =
    RankedRoute(mkRoute(distM, points, cqi, scenic), score)

  private def count(hay: String, needle: String): Int =
    needle.r.findAllMatchIn(hay).size

  private val p1 = LatLon(52.50, 13.40)
  private val p2 = LatLon(52.51, 13.41)

  // ── toGeoJson ─────────────────────────────────────────────────────────────

  test("toGeoJson wraps routes in a FeatureCollection"):
    val json = RouteExport.toGeoJson(List(mkRanked(1000.0, List(p1, p2), 0.6, 0.2, 0.4)))
    assert(json.contains("\"type\":\"FeatureCollection\""))

  test("toGeoJson emits one LineString Feature per route"):
    val routes = List(
      mkRanked(1000.0, List(p1, p2), 0.6, 0.2, 0.4),
      mkRanked(1200.0, List(p2, p1), 0.5, 0.3, 0.3)
    )
    assertEquals(count(RouteExport.toGeoJson(routes), "\"LineString\""), 2)

  test("toGeoJson emits coordinates in [lon,lat] order"):
    // p1 = (lat 52.50, lon 13.40) → GeoJSON coordinate [13.400000, 52.500000]
    val json = RouteExport.toGeoJson(List(mkRanked(1000.0, List(p1), 0.6, 0.2, 0.4)))
    assert(json.contains("[13.400000,52.500000]"))

  test("toGeoJson includes ranking + score properties"):
    val json = RouteExport.toGeoJson(List(mkRanked(1234.0, List(p1, p2), 0.69, 0.24, 0.465)))
    assert(json.contains("\"rank\":1"))
    assert(json.contains("\"distance_m\":1234.0"))
    assert(json.contains("\"blended_score\":0.4650"))
    assert(json.contains("\"mean_cqi\":0.6900"))
    assert(json.contains("\"mean_scenic\":0.2400"))
    assert(json.contains("\"stroke\":"))

  test("toGeoJson uses dot decimals under a comma locale"):
    val saved = Locale.getDefault().nn
    try
      Locale.setDefault(Locale.GERMANY.nn)
      val json = RouteExport.toGeoJson(List(mkRanked(1000.0, List(p1), 0.6, 0.2, 0.4)))
      assert(json.contains("13.400000"))
      assert(!json.contains("13,400000"))
    finally Locale.setDefault(saved)

  // ── toGpx ───────────────────────────────────────────────────────────────

  test("toGpx wraps tracks in a gpx 1.1 document"):
    val gpx = RouteExport.toGpx(List(mkRanked(1000.0, List(p1, p2), 0.6, 0.2, 0.4)), "berlin loop")
    assert(gpx.contains("<gpx version=\"1.1\""))
    assert(gpx.contains("</gpx>"))

  test("toGpx emits one trk per route"):
    val routes = List(
      mkRanked(1000.0, List(p1, p2), 0.6, 0.2, 0.4),
      mkRanked(1200.0, List(p2, p1), 0.5, 0.3, 0.3)
    )
    assertEquals(count(RouteExport.toGpx(routes, "x"), "<trk>"), 2)

  test("toGpx emits trkpt with lat/lon attributes"):
    val gpx = RouteExport.toGpx(List(mkRanked(1000.0, List(p1), 0.6, 0.2, 0.4)), "x")
    assert(gpx.contains("<trkpt lat=\"52.500000\" lon=\"13.400000\"/>"))

  test("toGpx escapes special characters in the track name"):
    val gpx = RouteExport.toGpx(List(mkRanked(1000.0, List(p1), 0.6, 0.2, 0.4)), "A & B <x>")
    assert(gpx.contains("A &amp; B &lt;x&gt;"))
    assert(!gpx.contains("A & B <x>"))

  test("toGpx uses dot decimals under a comma locale"):
    val saved = Locale.getDefault().nn
    try
      Locale.setDefault(Locale.GERMANY.nn)
      val gpx = RouteExport.toGpx(List(mkRanked(1000.0, List(p1), 0.6, 0.2, 0.4)), "x")
      assert(gpx.contains("lat=\"52.500000\""))
      assert(!gpx.contains("lat=\"52,500000\""))
    finally Locale.setDefault(saved)
