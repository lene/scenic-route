package scenicroute

import java.nio.file.{Files, Paths}

class ScenicRoutingTest extends munit.FunSuite:

  // Direct route (way 201): nodes 10→11, ~222 m
  // Scenic route (ways 202+203): nodes 10→12→11, ~301 m
  // With wI=wS=0: uniform priority → distance-minimising → direct wins (~222 m)
  // With wI=wS=0.5: scenic ways score ~5x higher priority per metre → scenic wins (>260 m)
  private val DirectMaxMeters = 250.0
  private val ScenicMinMeters = 260.0

  private lazy val router: Router =
    val osmFile    = Paths.get(getClass.getResource("/fixtures/parallel.osm.xml").toURI.nn)
    val scoresFile = Paths.get(getClass.getResource("/fixtures/parallel-scores.csv").toURI.nn)
    val graphDir   = Files.createTempDirectory("scenic-rte-test-")
    Router.fromOsm(osmFile, graphDir, scoresFile)

  private val start = LatLon(52.5000, 13.4000) // node 10
  private val end   = LatLon(52.5020, 13.4000) // node 11

  private def route(params: RouteParams): Route =
    router.route(start, end, params) match
      case Right(r) => r
      case Left(e)  => fail(e.errorMessage)

  test("stock weights: router picks direct (shorter) route"):
    val stock =
      RouteParams.default.copy(infraWeight = 0.0, scenicWeight = 0.0, gradientWeight = 0.0)
    assert(route(stock).distanceMeters < DirectMaxMeters)

  test("scenic weights: router picks scenic (longer) detour"):
    assert(route(RouteParams.default).distanceMeters > ScenicMinMeters)

  test("scenic route has higher mean scenic_quality than direct route"):
    val stock =
      RouteParams.default.copy(infraWeight = 0.0, scenicWeight = 0.0, gradientWeight = 0.0)
    val direct = route(stock)
    val scenic = route(RouteParams.default)
    assert(scenic.meanScenicQuality > direct.meanScenicQuality)
