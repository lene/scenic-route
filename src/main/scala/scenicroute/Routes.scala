package scenicroute

import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

/** tapir endpoints + http4s routing. Routing is injected as a function so the HTTP
  * layer is testable without a GraphHopper graph.
  */
// Any: tapir's endpoint capabilities type parameter is `Any` (no streaming); the
// DSL surfaces it in inferred types. Endpoints are otherwise fully typed.
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object Routes:

  /** Injected routing + geocoding: the HTTP layer stays testable without a graph or network. */
  type RouteFn   = (LatLon, LatLon, Double, RouteParams) => Seq[RankedRoute]
  type GeocodeFn = String => IO[List[GeoResult]]

  val health: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get.in("health").out(stringBody)

  val routes: PublicEndpoint[RouteReq, String, RouteResp, Any] =
    endpoint.post
      .in("routes")
      .in(jsonBody[RouteReq])
      .errorOut(stringBody)
      .out(jsonBody[RouteResp])

  val geocode: PublicEndpoint[String, String, List[GeoResult], Any] =
    endpoint.get
      .in("geocode")
      .in(query[String]("q"))
      .errorOut(stringBody)
      .out(jsonBody[List[GeoResult]])

  def httpRoutes(routeFn: RouteFn, geocodeFn: GeocodeFn): HttpRoutes[IO] =
    val healthServer = health.serverLogicSuccess[IO](_ => IO.pure("ok"))
    val routesServer = routes.serverLogic[IO] { req =>
      IO {
        Api.toDomain(req).map { plan =>
          Api.toResponse(routeFn(plan.start, plan.end, plan.targetKm, plan.params))
        }
      }
    }
    val geocodeServer =
      geocode.serverLogic[IO](q => geocodeFn(q).map(rs => Right[String, List[GeoResult]](rs)))
    Http4sServerInterpreter[IO]().toRoutes(List(healthServer, routesServer, geocodeServer))
