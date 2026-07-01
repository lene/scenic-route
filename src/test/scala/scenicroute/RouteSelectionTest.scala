package scenicroute

import com.graphhopper.util.details.PathDetail

class RouteSelectionTest extends munit.FunSuite:

  // Minimal Route builder — stubs PathDetail to return fixed EV values
  private def mkRoute(distM: Double, points: List[LatLon], cqi: Double, scenic: Double): Route =
    val cqiDetail = new PathDetail(0):
      // AsInstanceOf: boxing Scala Double to AnyRef for PathDetail contract; safe.
      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      override def getValue: AnyRef = Double.box(cqi)
      override def getLength: Int   = 1
    val scenicDetail = new PathDetail(0):
      @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
      override def getValue: AnyRef = Double.box(scenic)
      override def getLength: Int   = 1
    Route(distM, points, List(cqiDetail), List(scenicDetail))

  private val p1 = LatLon(52.50, 13.40)
  private val p2 = LatLon(52.51, 13.40)
  private val p3 = LatLon(52.52, 13.40)
  private val p4 = LatLon(52.53, 13.40)

  private val params = RouteParams.default

  // ── filterByDistance ────────────────────────────────────────────────────────

  test("filterByDistance keeps route at lower bound"):
    val r = mkRoute(8500.0, List(p1, p2), 0.5, 0.5) // 0.85 * 10000
    assertEquals(RouteSelection.filterByDistance(List(r), 10000.0, params).size, 1)

  test("filterByDistance keeps route at upper bound"):
    val r = mkRoute(11500.0, List(p1, p2), 0.5, 0.5) // 1.15 * 10000
    assertEquals(RouteSelection.filterByDistance(List(r), 10000.0, params).size, 1)

  test("filterByDistance drops route below lower bound"):
    val r = mkRoute(8499.0, List(p1, p2), 0.5, 0.5)
    assertEquals(RouteSelection.filterByDistance(List(r), 10000.0, params).size, 0)

  test("filterByDistance drops route above upper bound"):
    val r = mkRoute(11501.0, List(p1, p2), 0.5, 0.5)
    assertEquals(RouteSelection.filterByDistance(List(r), 10000.0, params).size, 0)

  // ── overlap ─────────────────────────────────────────────────────────────────

  test("overlap of identical routes is 1.0"):
    val r = mkRoute(1000.0, List(p1, p2, p3), 0.5, 0.5)
    assertEqualsDouble(RouteSelection.overlap(r, r), 1.0, 0.001)

  test("overlap of disjoint routes is 0.0"):
    val a = mkRoute(1000.0, List(p1, p2), 0.5, 0.5)
    val b = mkRoute(1000.0, List(p3, p4), 0.5, 0.5)
    assertEqualsDouble(RouteSelection.overlap(a, b), 0.0, 0.001)

  test("overlap of half-shared routes is 0.5"):
    val a = mkRoute(1000.0, List(p1, p2, p3), 0.5, 0.5)
    val b = mkRoute(1000.0, List(p2, p3, p4), 0.5, 0.5)
    // |{p2,p3}| / |{p1,p2,p3,p4}| = 2/4 = 0.5
    assertEqualsDouble(RouteSelection.overlap(a, b), 0.5, 0.001)

  // ── dedupe ──────────────────────────────────────────────────────────────────

  test("dedupe keeps two fully disjoint routes"):
    val a = RankedRoute(mkRoute(1000.0, List(p1, p2), 0.8, 0.8), 0.8)
    val b = RankedRoute(mkRoute(1000.0, List(p3, p4), 0.6, 0.6), 0.6)
    assertEquals(RouteSelection.dedupe(List(a, b), 0.7).size, 2)

  test("dedupe drops second route when overlap exceeds threshold"):
    val r = mkRoute(1000.0, List(p1, p2, p3), 0.5, 0.5)
    val a = RankedRoute(r, 0.8)
    val b = RankedRoute(r, 0.6) // identical → overlap 1.0 > 0.7
    assertEquals(RouteSelection.dedupe(List(a, b), 0.7).size, 1)

  test("dedupe keeps the higher-scored route (first in ranked input)"):
    val r = mkRoute(1000.0, List(p1, p2, p3), 0.5, 0.5)
    val kept = RouteSelection.dedupe(List(RankedRoute(r, 0.8), RankedRoute(r, 0.6)), 0.7)
    kept.headOption match
      case Some(rr) => assertEqualsDouble(rr.blendedScore, 0.8, 0.001)
      case None     => fail("expected one route to be kept")

  // ── rankAndSelect ────────────────────────────────────────────────────────────

  test("rankAndSelect orders by blendedScore descending"):
    val low  = mkRoute(1000.0, List(p1, p2), 0.2, 0.2)
    val high = mkRoute(1000.0, List(p3, p4), 0.8, 0.8)
    val result = RouteSelection.rankAndSelect(List(low, high), params)
    result match
      case first :: second :: Nil =>
        assert(first.blendedScore > second.blendedScore)
      case _ =>
        fail("expected exactly 2 ranked results")

  test("rankAndSelect caps at 5 even when numSuggestions is higher"):
    val bigParams = params.copy(numSuggestions = 10)
    val routes = (1 to 8).map(i => mkRoute(1000.0, List(LatLon(52.50 + i * 0.01, 13.40)), 0.5, 0.5)).toList
    assert(RouteSelection.rankAndSelect(routes, bigParams).sizeIs <= 5)

  test("rankAndSelect returns at most numSuggestions routes"):
    val routes = (1 to 3).map(i => mkRoute(1000.0, List(LatLon(52.50 + i * 0.01, 13.40)), 0.5, 0.5)).toList
    assertEquals(RouteSelection.rankAndSelect(routes, params.copy(numSuggestions = 2)).size, 2)
