package scenicroute

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.graphhopper.util.details.PathDetail
import io.circe.parser.decode
import org.http4s.implicits.*
import org.http4s.{Method, Request, Response, Status}

// Any: http4s Request/withEntity/uri builders surface Any in inferred types.
@SuppressWarnings(Array("org.wartremover.warts.Any"))
class RoutesTest extends munit.FunSuite:

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

  // stub router: two fixed routes regardless of input (no graph needed)
  private val stub: Routes.RouteFn = (_, _, _, _) =>
    List(
      RankedRoute(mkRoute(1000.0, List(LatLon(52.5, 13.4), LatLon(52.51, 13.41)), 0.6, 0.2), 0.4),
      RankedRoute(mkRoute(1200.0, List(LatLon(52.51, 13.41), LatLon(52.5, 13.4)), 0.5, 0.3), 0.3)
    )

  private val validJson =
    """{"start":{"lat":52.5,"lon":13.4},"end":{"lat":52.42,"lon":13.65},"targetKm":30,
       |"params":{"infraWeight":0.5,"scenicWeight":0.5,"distanceToleranceLow":0.85,
       |"distanceToleranceHigh":1.15,"numSuggestions":4}}""".stripMargin

  private def post(body: String): Response[IO] =
    val req = Request[IO](Method.POST, uri"/routes").withEntity(body)
    Routes.httpRoutes(stub).orNotFound.run(req).unsafeRunSync()

  test("POST /routes returns 200 with the ranked routes"):
    val resp = post(validJson)
    assertEquals(resp.status, Status.Ok)
    decode[RouteResp](resp.as[String].unsafeRunSync()) match
      case Right(r) => assertEquals(r.routes.size, 2)
      case Left(_)  => fail("response did not decode as RouteResp")

  test("POST /routes rejects an invalid request with 400"):
    val bad = validJson.replace("\"targetKm\":30", "\"targetKm\":0")
    assertEquals(post(bad).status, Status.BadRequest)

  test("GET /health returns ok"):
    val req  = Request[IO](Method.GET, uri"/health")
    val resp = Routes.httpRoutes(stub).orNotFound.run(req).unsafeRunSync()
    assertEquals(resp.status, Status.Ok)
    assertEquals(resp.as[String].unsafeRunSync(), "ok")
