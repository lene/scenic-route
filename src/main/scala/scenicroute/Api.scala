package scenicroute

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Json}

// ── wire DTOs (circe codecs in companions) ────────────────────────────────────

final case class PointDto(lat: Double, lon: Double)
object PointDto:
  given Decoder[PointDto] = deriveDecoder
  given Encoder[PointDto] = deriveEncoder

/** The subset of RouteParams the UI exposes; gradient + overlap use defaults. */
final case class ParamsDto(
    infraWeight: Double,
    scenicWeight: Double,
    distanceToleranceLow: Double,
    distanceToleranceHigh: Double,
    numSuggestions: Int
)
object ParamsDto:
  given Decoder[ParamsDto] = deriveDecoder
  given Encoder[ParamsDto] = deriveEncoder

final case class RouteReq(start: PointDto, end: PointDto, targetKm: Double, params: ParamsDto)
object RouteReq:
  given Decoder[RouteReq] = deriveDecoder
  given Encoder[RouteReq] = deriveEncoder

final case class RouteDto(
    rank: Int,
    distanceM: Double,
    blendedScore: Double,
    meanCqi: Double,
    meanScenic: Double,
    gpx: String
)
object RouteDto:
  given Decoder[RouteDto] = deriveDecoder
  given Encoder[RouteDto] = deriveEncoder

final case class RouteResp(geojson: Json, routes: List[RouteDto])
object RouteResp:
  given Decoder[RouteResp] = deriveDecoder
  given Encoder[RouteResp] = deriveEncoder

/** Validated domain form of a request, ready for `Router.routeWithTarget`. */
final case class RoutePlan(start: LatLon, end: LatLon, targetKm: Double, params: RouteParams)

/** Pure translation + validation between wire DTOs and the routing domain. */
object Api:

  def toDomain(req: RouteReq): Either[String, RoutePlan] =
    for
      start <- point(req.start)
      end   <- point(req.end)
      _     <- cond(req.targetKm > 0.0, "targetKm must be > 0")
      p     <- toParams(req.params)
    yield RoutePlan(start, end, req.targetKm, p)

  def toResponse(routes: Seq[RankedRoute]): RouteResp =
    // parse never fails — it's our own toGeoJson output; empty object is an unreachable fallback.
    val geo = parse(RouteExport.toGeoJson(routes)).getOrElse(Json.obj())
    val dtos = routes.zipWithIndex.map: (rr, i) =>
      RouteDto(
        rank = i + 1,
        distanceM = rr.route.distanceMeters,
        blendedScore = rr.blendedScore,
        meanCqi = rr.route.meanCqiQuality,
        meanScenic = rr.route.meanScenicQuality,
        gpx = RouteExport.toGpx(List(rr), s"scenic-route ${i + 1}")
      )
    RouteResp(geo, dtos.toList)

  private def cond(test: Boolean, msg: String): Either[String, Unit] =
    Either.cond(test, (), msg)

  private def inUnit(x: Double): Boolean = x >= 0.0 && x <= 1.0

  private def point(p: PointDto): Either[String, LatLon] =
    for
      _ <- cond(p.lat >= -90.0 && p.lat <= 90.0, "lat out of range")
      _ <- cond(p.lon >= -180.0 && p.lon <= 180.0, "lon out of range")
    yield LatLon(p.lat, p.lon)

  private def toParams(p: ParamsDto): Either[String, RouteParams] =
    for
      _ <- cond(inUnit(p.infraWeight), "infraWeight must be in [0,1]")
      _ <- cond(inUnit(p.scenicWeight), "scenicWeight must be in [0,1]")
      _ <- cond(
        p.distanceToleranceLow > 0.0 && p.distanceToleranceLow <= 1.0,
        "distanceToleranceLow must be in (0,1]"
      )
      _ <- cond(p.distanceToleranceHigh >= 1.0, "distanceToleranceHigh must be >= 1")
      _ <- cond(p.distanceToleranceLow <= p.distanceToleranceHigh, "tolerance low must be <= high")
      _ <- cond(p.numSuggestions >= 1 && p.numSuggestions <= 5, "numSuggestions must be in [1,5]")
    yield RouteParams.default.copy(
      infraWeight = p.infraWeight,
      scenicWeight = p.scenicWeight,
      gradientWeight = 0.0,
      distanceToleranceLow = p.distanceToleranceLow,
      distanceToleranceHigh = p.distanceToleranceHigh,
      numSuggestions = p.numSuggestions
    )
