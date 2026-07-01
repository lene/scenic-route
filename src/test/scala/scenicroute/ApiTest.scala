package scenicroute

import com.graphhopper.util.details.PathDetail
import io.circe.parser.decode

class ApiTest extends munit.FunSuite:

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

  private def mkRanked(distM: Double, pts: List[LatLon], cqi: Double, sc: Double, score: Double) =
    RankedRoute(mkRoute(distM, pts, cqi, sc), score)

  private val p1 = LatLon(52.50, 13.40)
  private val p2 = LatLon(52.51, 13.41)

  private val validParams = ParamsDto(0.5, 0.5, 0.85, 1.15, 4)
  private val validReq = RouteReq(PointDto(52.5, 13.4), PointDto(52.42, 13.65), 30.0, validParams)

  // ── wire decoding ───────────────────────────────────────────────────────────

  test("decode RouteReq from wire JSON"):
    val json =
      """{"start":{"lat":52.5,"lon":13.4},"end":{"lat":52.42,"lon":13.65},"targetKm":30,
         |"params":{"infraWeight":0.5,"scenicWeight":0.5,"distanceToleranceLow":0.85,
         |"distanceToleranceHigh":1.15,"numSuggestions":4}}""".stripMargin
    assertEquals(decode[RouteReq](json), Right[io.circe.Error, RouteReq](validReq))

  // ── toDomain validation ──────────────────────────────────────────────────────

  test("toDomain accepts a valid request"):
    val expected = RoutePlan(
      LatLon(52.5, 13.4),
      LatLon(52.42, 13.65),
      30.0,
      RouteParams.default.copy(infraWeight = 0.5, scenicWeight = 0.5, gradientWeight = 0.0)
    )
    assertEquals(Api.toDomain(validReq), Right[String, RoutePlan](expected))

  test("toDomain rejects non-positive targetKm"):
    assert(Api.toDomain(validReq.copy(targetKm = 0.0)).isLeft)

  test("toDomain rejects a weight outside [0,1]"):
    assert(Api.toDomain(validReq.copy(params = validParams.copy(infraWeight = 1.5))).isLeft)

  test("toDomain rejects an out-of-range coordinate"):
    assert(Api.toDomain(validReq.copy(start = PointDto(99.9, 13.4))).isLeft)

  test("toDomain rejects numSuggestions outside [1,5]"):
    assert(Api.toDomain(validReq.copy(params = validParams.copy(numSuggestions = 6))).isLeft)

  test("toDomain rejects inverted tolerance bounds"):
    assert(
      Api.toDomain(validReq.copy(params = validParams.copy(distanceToleranceLow = 1.2))).isLeft
    )

  // ── toResponse ───────────────────────────────────────────────────────────────

  test("toResponse carries one dto per route, ranked, with gpx"):
    val resp = Api.toResponse(
      List(
        mkRanked(1000.0, List(p1, p2), 0.6, 0.2, 0.4),
        mkRanked(1200.0, List(p2, p1), 0.5, 0.3, 0.3)
      )
    )
    assertEquals(resp.routes.size, 2)
    assertEquals(resp.routes.map(_.rank), List(1, 2))
    assert(resp.routes.headOption.exists(_.gpx.contains("<gpx")))

  test("toResponse geojson is a FeatureCollection"):
    val resp = Api.toResponse(List(mkRanked(1000.0, List(p1, p2), 0.6, 0.2, 0.4)))
    assertEquals(resp.geojson.hcursor.get[String]("type").toOption, Some("FeatureCollection"))
