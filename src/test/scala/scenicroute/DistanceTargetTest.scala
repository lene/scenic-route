package scenicroute

import java.nio.file.{Files, Paths}

class DistanceTargetTest extends munit.FunSuite:

  // Parallel fixture geometry (from ScenicRoutingTest / ScenicEncodingTest):
  //   Node 10 (52.5000, 13.4000) — start/end
  //   Node 11 (52.5020, 13.4000) — end for A→B tests
  //   Node 12 (52.5010, 13.4015) — scenic detour midpoint
  //   Way 201 (10→11 direct):     ~222 m, low scenic scores
  //   Ways 202+203 (10→12→11):    ~301 m, high scenic scores
  //   Triangle loop 10→12→11→10:  ~582 m
  private lazy val router: Router =
    val osmFile    = Paths.get(getClass.getResource("/fixtures/parallel.osm.xml").toURI.nn)
    val scoresFile = Paths.get(getClass.getResource("/fixtures/parallel-scores.csv").toURI.nn)
    val graphDir   = Files.createTempDirectory("dist-target-test-")
    Router.fromOsm(osmFile, graphDir, scoresFile)

  private val node10 = LatLon(52.5000, 13.4000)
  private val node11 = LatLon(52.5020, 13.4000)

  // ── A→B: target near scenic detour length ────────────────────────────────────
  // Target = 0.300 km (300 m). Window [0.85·300, 1.15·300] = [255, 345] m.
  // The scenic route (ways 202+203 ≈ 301 m) is in the window; direct (222 m) is not.

  test("A→B routeWithTarget returns at least one route in the distance window"):
    val results = router.routeWithTarget(node10, node11, 0.3, RouteParams.default)
    assert(results.nonEmpty, "expected at least one route in the [255, 345] m window")

  test("A→B all returned routes are within [0.85, 1.15] × target distance"):
    val target  = 0.3 // km
    val lo      = 0.85 * target * 1000
    val hi      = 1.15 * target * 1000
    val results = router.routeWithTarget(node10, node11, target, RouteParams.default)
    results.foreach: rr =>
      assert(
        rr.route.distanceMeters >= lo && rr.route.distanceMeters <= hi,
        "route distance outside tolerance window"
      )

  test("A→B results are ordered by blendedScore descending"):
    val results = router.routeWithTarget(node10, node11, 0.3, RouteParams.default).toList
    results match
      case Nil | _ :: Nil => () // 0 or 1 result — ordering trivially satisfied
      case _ =>
        val scores = results.map(_.blendedScore)
        assert(scores.zip(scores.drop(1)).forall((a, b) => a >= b), "results not sorted descending")

  test("A→B result count does not exceed numSuggestions"):
    val results = router.routeWithTarget(node10, node11, 0.3, RouteParams.default)
    assert(results.sizeIs <= RouteParams.default.numSuggestions)

  // ── Loop: start == end ────────────────────────────────────────────────────────
  // Target = 0.600 km (600 m). Window [510, 690] m.
  // Triangle loop 10→12→11→10 ≈ 582 m is within the window.

  test("loop routeWithTarget (start == end) returns at least one route"):
    val results = router.routeWithTarget(node10, node10, 0.6, RouteParams.default)
    assert(results.nonEmpty, "expected at least one loop in the [510, 690] m window")

  test("loop all returned routes have positive distance"):
    val results = router.routeWithTarget(node10, node10, 0.6, RouteParams.default)
    results.foreach: rr =>
      assert(rr.route.distanceMeters > 0)

  test("loop all returned routes are within [0.85, 1.15] × target distance"):
    val target  = 0.6
    val lo      = 0.85 * target * 1000
    val hi      = 1.15 * target * 1000
    val results = router.routeWithTarget(node10, node10, target, RouteParams.default)
    results.foreach: rr =>
      assert(
        rr.route.distanceMeters >= lo && rr.route.distanceMeters <= hi,
        "loop distance outside tolerance window"
      )
