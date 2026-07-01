package scenicroute

import com.graphhopper.config.{LMProfile, Profile}
import com.graphhopper.json.Statement
import com.graphhopper.json.Statement.Op
import com.graphhopper.util.CustomModel
import com.graphhopper.util.Parameters.Algorithms
import com.graphhopper.util.details.PathDetail
import com.graphhopper.util.shapes.GHPoint
import com.graphhopper.{GHRequest, GHResponse, GraphHopper, GraphHopperConfig}

import java.nio.file.Path
import java.util
import scala.jdk.CollectionConverters.*
import scala.math.{cos, Pi, sqrt}

final class Router private (gh: GraphHopper):

  /** Route from [[start]] to [[end]] with explicit per-request blend weights. */
  def route(start: LatLon, end: LatLon, params: RouteParams): Either[RouteError, Route] =
    val rsp = gh.route(buildRequest(List(start, end), params))
    if rsp.hasErrors() then
      val errors = rsp.getErrors().nn
      val msg =
        if errors.isEmpty then "unknown routing error"
        else errors.get(0).nn.getMessage.nn
      Left[RouteError, Route](RouteError.RoutingError(msg))
    else
      Right[RouteError, Route](extractRoute(rsp))

  /** Return up to `params.numSuggestions` ranked routes near `targetKm` km.
    *
    * When `start == end` (loop ride), uses GH's `round_trip` algorithm seeded
    * with multiple values.  For distinct endpoints, samples perpendicular-bisector
    * via points and routes `start → via → end`.  Both feeds the shared
    * filter → dedupe → rank pipeline.
    */
  def routeWithTarget(start: LatLon, end: LatLon, targetKm: Double, params: RouteParams): Seq[RankedRoute] =
    val targetM    = targetKm * 1000
    val candidates =
      if isLoop(start, end) then loopCandidates(start, targetM, params)
      else viaCandidates(start, end, targetM, params)
    val filtered = RouteSelection.filterByDistance(candidates, targetM, params)
    RouteSelection.rankAndSelect(filtered, params)

  // ── candidate generators ───────────────────────────────────────────────────

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def loopCandidates(start: LatLon, targetM: Double, params: RouteParams): Seq[Route] =
    (0 until Router.LoopSeeds).flatMap: seed =>
      val req = buildRequest(List(start), params).setAlgorithm(Algorithms.ROUND_TRIP)
      req.getHints().nn
        .putObject(Algorithms.RoundTrip.DISTANCE, targetM)
        .putObject(Algorithms.RoundTrip.SEED, seed.toLong)
        .putObject(Algorithms.RoundTrip.POINTS, 3)
      val rsp = gh.route(req)
      if rsp.hasErrors() then None
      else Option(rsp.getBest()).map(_ => extractRoute(rsp))

  private def viaCandidates(start: LatLon, end: LatLon, targetM: Double, params: RouteParams): Seq[Route] =
    computeVias(start, end, targetM).flatMap: via =>
      val rsp = gh.route(buildRequest(List(start, via, end), params))
      if rsp.hasErrors() then None
      else Option(rsp.getBest()).map(_ => extractRoute(rsp))

  // ── geometry ───────────────────────────────────────────────────────────────

  // ponytail: perpendicular-midpoint offset; proper ellipse sampling if landing rate falls below 30%.
  private def computeVias(a: LatLon, b: LatLon, targetM: Double): List[LatLon] =
    val latScale = 111320.0
    val lonScale = 111320.0 * cos(a.lat * Pi / 180.0)
    val midLat   = (a.lat + b.lat) / 2
    val midLon   = (a.lon + b.lon) / 2
    val dyM      = (b.lat - a.lat) * latScale
    val dxM      = (b.lon - a.lon) * lonScale
    val dM       = sqrt(dyM * dyM + dxM * dxM)
    val T2       = targetM / 2
    val D2       = dM / 2
    val hMax     = if T2 > D2 then sqrt(T2 * T2 - D2 * D2) else 0.0
    if hMax <= 0.0 then List(LatLon(midLat, midLon))
    else
      // perpendicular unit vector (rotate AB 90°)
      val pyU = dxM / dM
      val pxU = -dyM / dM
      Router.ViaFracs.flatMap: f =>
        val h = hMax * f
        List(
          LatLon(midLat + h * pyU / latScale, midLon + h * pxU / lonScale),
          LatLon(midLat - h * pyU / latScale, midLon - h * pxU / lonScale)
        )

  // ── shared helpers ─────────────────────────────────────────────────────────

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def buildRequest(points: List[LatLon], params: RouteParams): GHRequest =
    val cqiExpr    = s"0.2 + ${params.infraWeight.toString} * cqi_quality"
    val scenicExpr = s"0.2 + ${params.scenicWeight.toString} * scenic_quality"
    val model = CustomModel()
      .addToPriority(Statement.If("true", Op.MULTIPLY, cqiExpr))
      .addToPriority(Statement.If("true", Op.MULTIPLY, scenicExpr))
    GHRequest(points.map(p => GHPoint(p.lat, p.lon)).asJava)
      .setProfile(Router.ProfileName)
      .setCustomModel(model)
      .setPathDetails(util.List.of("cqi_quality", "scenic_quality"))

  private def extractRoute(rsp: GHResponse): Route =
    val path    = rsp.getBest().nn
    val ptList  = path.getPoints().nn
    val points  = (0 until ptList.size()).map(i => LatLon(ptList.getLat(i), ptList.getLon(i))).toList
    val details = path.getPathDetails().nn
    val cqiD: List[PathDetail]    = Option(details.get("cqi_quality")).fold(List.empty)(_.nn.asScala.toList)
    val scenicD: List[PathDetail] = Option(details.get("scenic_quality")).fold(List.empty)(_.nn.asScala.toList)
    Route(path.getDistance(), points, cqiD, scenicD)

  // Loop detection: start and end are the same coordinate (within float rounding)
  private def isLoop(a: LatLon, b: LatLon): Boolean =
    (a.lat - b.lat).abs < 1e-9 && (a.lon - b.lon).abs < 1e-9

object Router:
  private val ProfileName = "bike"
  private val LoopSeeds   = 12
  // Via fractions of hMax (perpendicular offset); spike showed f>0.85 consistently overshoots.
  private val ViaFracs    = List(0.30, 0.40, 0.55, 0.70, 0.85)

  def fromOsm(osmFile: Path, graphCache: Path, scoreFile: Path): Router =
    val scores = ScoreStore.load(scoreFile)
    new Router(buildHopper(osmFile.toString, graphCache.toString, scores))

  private def buildHopper(osmFile: String, graphCacheDir: String, scores: Map[Long, WayQuality]): GraphHopper =
    val customModel = CustomModel().addToSpeed(Statement.If("true", Op.LIMIT, "15"))
    val config = GraphHopperConfig()
      .putObject("graph.location", graphCacheDir)
      .putObject("datareader.file", osmFile)
      .putObject("import.osm.ignored_highways", util.List.of("motorway", "trunk"))
      .putObject("graph.encoded_values", "cqi_quality,scenic_quality")
      .setProfiles(util.List.of(Profile(ProfileName).setCustomModel(customModel)))
      .setLMProfiles(util.List.of(LMProfile(ProfileName)))
    val gh = GraphHopper()
    gh.setImportRegistry(ScenicImportRegistry(scores))
    gh.init(config)
    gh.importOrLoad()
    gh
