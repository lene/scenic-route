package scenicroute

import java.nio.file.{Files, Paths}

class ScenicEncodingTest extends munit.FunSuite:

  // way 202: cqi=80, lts=1, green=0.9, blue=0.8
  // → cqiQuality   = 0.7*(80/100) + 0.3*((4-1)/3) = 0.56 + 0.30 = 0.86
  // → scenicQuality = 0.5*0.9 + 0.5*0.8           = 0.85
  private val ExpectedCqi    = 0.86
  private val ExpectedScenic = 0.85
  private val Tolerance      = 0.01 // GH stores at 0.01 resolution (7-bit EV)

  private lazy val router: Router =
    val osmFile    = Paths.get(getClass.getResource("/fixtures/parallel.osm.xml").toURI.nn)
    val scoresFile = Paths.get(getClass.getResource("/fixtures/parallel-scores.csv").toURI.nn)
    val graphDir   = Files.createTempDirectory("scenic-enc-test-")
    Router.fromOsm(osmFile, graphDir, scoresFile)

  // Route 10 → 12 traverses only way 202; its path-detail means should equal derived values.
  private lazy val route10to12: Route =
    val start = LatLon(52.5000, 13.4000) // node 10
    val end   = LatLon(52.5010, 13.4015) // node 12
    router.route(start, end, RouteParams.default) match
      case Right(r) => r
      case Left(e)  => fail(e.errorMessage)

  test("EV round-trip: mean cqi_quality on scenic way matches derived value"):
    assertEqualsDouble(route10to12.meanCqiQuality, ExpectedCqi, Tolerance)

  test("EV round-trip: mean scenic_quality on scenic way matches derived value"):
    assertEqualsDouble(route10to12.meanScenicQuality, ExpectedScenic, Tolerance)
