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

  // ── doubledFraction ───────────────────────────────────────────────────────────

  test("doubledFraction of a clean, non-repeating path is 0.0"):
    val r = mkRoute(1000.0, List(p1, p2, p3, p4), 0.5, 0.5)
    assertEqualsDouble(RouteSelection.doubledFraction(r), 0.0, 0.001)

  test("doubledFraction of a pure out-and-back is 1.0"):
    // out to p3 then back: every segment ridden twice
    val r = mkRoute(1000.0, List(p1, p2, p3, p2, p1), 0.5, 0.5)
    assertEqualsDouble(RouteSelection.doubledFraction(r), 1.0, 0.001)

  test("doubledFraction of a single-point route is 0.0"):
    val r = mkRoute(0.0, List(p1), 0.5, 0.5)
    assertEqualsDouble(RouteSelection.doubledFraction(r), 0.0, 0.001)

  test("doubledFraction of a loop with a short spur is small but positive"):
    // Triangle-ish loop p1→p2→p3→p1 plus an out-and-back spur p1→pSpur→p1.
    // p1..p3 collinear (equal legs), spur is a shorter leg ridden twice.
    val pSpur = LatLon(52.495, 13.40) // half a leg south of p1
    val r     = mkRoute(1000.0, List(p1, p2, p3, p1, pSpur, p1), 0.5, 0.5)
    val f     = RouteSelection.doubledFraction(r)
    assert(f > 0.0, "expected positive doubling")
    assert(f < 0.5, "expected mostly-clean loop")

  // ── blendedScore penalty ──────────────────────────────────────────────────────

  test("blendedScore demotes a doubled route below an equal-quality clean one"):
    val clean   = mkRoute(1000.0, List(p1, p2, p3, p4), 0.6, 0.6)
    val doubled = mkRoute(1000.0, List(p1, p2, p3, p2, p1), 0.6, 0.6)
    assert(
      RouteSelection.blendedScore(clean, params) > RouteSelection.blendedScore(doubled, params)
    )

  test("blendedScore penalty is disabled at doubledPenaltyWeight = 0"):
    val noPenalty = params.copy(doubledPenaltyWeight = 0.0)
    val doubled   = mkRoute(1000.0, List(p1, p2, p3, p2, p1), 0.6, 0.6)
    // quality only: 0.5*0.6 + 0.5*0.6 = 0.6
    assertEqualsDouble(RouteSelection.blendedScore(doubled, noPenalty), 0.6, 0.001)

  test("rankAndSelect drops a doubled route entirely at high penalty weight"):
    // doubled has higher quality, but fraction 1.0 > cap(0.8) = 0.44 → filtered out
    val clean   = mkRoute(1000.0, List(p1, p2, p3, p4), 0.5, 0.5)
    val doubled = mkRoute(1000.0, List(p1, p2, p3, p2, p1), 0.9, 0.9)
    val result = RouteSelection.rankAndSelect(List(doubled, clean), params.copy(numSuggestions = 1))
    result.map(_.route.points) match
      case pts :: Nil => assertEquals(pts, List(p1, p2, p3, p4))
      case _          => fail("expected exactly the clean route")

  test("rankAndSelect keeps the doubled route when the penalty weight is 0"):
    val clean   = mkRoute(1000.0, List(p1, p2, p3, p4), 0.5, 0.5)
    val doubled = mkRoute(1000.0, List(p1, p2, p3, p2, p1), 0.9, 0.9)
    val noPen   = params.copy(doubledPenaltyWeight = 0.0, numSuggestions = 1)
    RouteSelection.rankAndSelect(List(doubled, clean), noPen).map(_.route.points) match
      case pts :: Nil => assertEquals(pts, List(p1, p2, p3, p2, p1))
      case _          => fail("expected exactly the doubled route")

  test("rankAndSelect falls back to doubled routes when nothing cleaner exists"):
    val doubled = mkRoute(1000.0, List(p1, p2, p3, p2, p1), 0.6, 0.6)
    val strict  = params.copy(doubledPenaltyWeight = 1.0)
    assert(RouteSelection.rankAndSelect(List(doubled), strict).nonEmpty)

  test("rankAndSelect ranks a clean loop above an equal-quality out-and-back"):
    val clean   = mkRoute(1000.0, List(p1, p2, p3, p4), 0.6, 0.6)
    val doubled = mkRoute(1000.0, List(p1, p2, p3, p2, p1), 0.6, 0.6)
    RouteSelection.rankAndSelect(List(doubled, clean), params) match
      case first :: _ => assertEqualsDouble(first.blendedScore, 0.6, 0.001) // the clean one
      case Nil        => fail("expected ranked results")

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
    val r    = mkRoute(1000.0, List(p1, p2, p3), 0.5, 0.5)
    val kept = RouteSelection.dedupe(List(RankedRoute(r, 0.8), RankedRoute(r, 0.6)), 0.7)
    kept.headOption match
      case Some(rr) => assertEqualsDouble(rr.blendedScore, 0.8, 0.001)
      case None     => fail("expected one route to be kept")

  // ── rankAndSelect ────────────────────────────────────────────────────────────

  test("rankAndSelect orders by blendedScore descending"):
    val low    = mkRoute(1000.0, List(p1, p2), 0.2, 0.2)
    val high   = mkRoute(1000.0, List(p3, p4), 0.8, 0.8)
    val result = RouteSelection.rankAndSelect(List(low, high), params)
    result match
      case first :: second :: Nil =>
        assert(first.blendedScore > second.blendedScore)
      case _ =>
        fail("expected exactly 2 ranked results")

  test("rankAndSelect caps at 5 even when numSuggestions is higher"):
    val bigParams = params.copy(numSuggestions = 10)
    val routes =
      (1 to 8).map(i => mkRoute(1000.0, List(LatLon(52.50 + i * 0.01, 13.40)), 0.5, 0.5)).toList
    assert(RouteSelection.rankAndSelect(routes, bigParams).sizeIs <= 5)

  test("rankAndSelect returns at most numSuggestions routes"):
    val routes =
      (1 to 3).map(i => mkRoute(1000.0, List(LatLon(52.50 + i * 0.01, 13.40)), 0.5, 0.5)).toList
    assertEquals(RouteSelection.rankAndSelect(routes, params.copy(numSuggestions = 2)).size, 2)
