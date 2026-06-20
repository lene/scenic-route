package scenicroute

import java.nio.file.{Files, Paths}

class RouterTest extends munit.FunSuite:

  // Tiny OSM XML fixture: nodes 1→2→3 along a cycleway ~110m apart (≈0.001° lat)
  private val osmXml = Paths.get(getClass.getResource("/fixtures/test.osm.xml").toURI)

  private lazy val router: Router =
    val graphDir = Files.createTempDirectory("scenic-route-test-graph-")
    Router.fromOsm(osmXml, graphDir)

  private def routeOrFail(start: LatLon, end: LatLon): Route =
    router.route(start, end) match
      case Right(r) => r
      case Left(e)  => fail(e.errorMessage)

  test("route between two nodes returns a non-empty path"):
    val start = LatLon(lat = 52.5000, lon = 13.4000)
    val end   = LatLon(lat = 52.5020, lon = 13.4000)
    assert(router.route(start, end).isRight)

  test("routed path has positive distance"):
    val start = LatLon(lat = 52.5000, lon = 13.4000)
    val end   = LatLon(lat = 52.5020, lon = 13.4000)
    val route = routeOrFail(start, end)
    assert(route.distanceMeters > 0)

  test("routed path has at least two points"):
    val start = LatLon(lat = 52.5000, lon = 13.4000)
    val end   = LatLon(lat = 52.5020, lon = 13.4000)
    val route = routeOrFail(start, end)
    assert(route.points.sizeIs >= 2)

  test("routing to unreachable location returns Left"):
    val start  = LatLon(lat = 52.5000, lon = 13.4000)
    val remote = LatLon(lat = 0.0, lon = 0.0) // Gulf of Guinea — not in graph
    assert(router.route(start, remote).isLeft)
