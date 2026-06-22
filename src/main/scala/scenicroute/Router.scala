package scenicroute

import com.graphhopper.config.{LMProfile, Profile}
import com.graphhopper.json.Statement
import com.graphhopper.json.Statement.Op
import com.graphhopper.util.CustomModel
import com.graphhopper.util.details.PathDetail
import com.graphhopper.{GHRequest, GHResponse, GraphHopper, GraphHopperConfig}

import java.nio.file.Path
import java.util
import scala.jdk.CollectionConverters.*

final class Router private (gh: GraphHopper):

  /** Route from [[start]] to [[end]] with explicit per-request blend weights.
    *
    * The priority expression blends infrastructure quality and scenic quality with a 0.2 floor
    * so that even low-scoring roads remain traversable.  Pass [[RouteParams.default]] for
    * equal infra/scenic weighting.
    */
  // Any: Double values in string interpolation widen to Any via the s"" macro — safe here.
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def route(start: LatLon, end: LatLon, params: RouteParams): Either[RouteError, Route] =
    val wI = params.infraWeight
    val wS = params.scenicWeight
    // GH 11.0 limits each priority expression to a single encoded value.
    // We approximate the additive blend with two successive multipliers, one per EV.
    // Each statement: floor 0.2 + (weight * EV), applied as (cqi_stmt) * (scenic_stmt).
    // This is a multiplicative proxy for the additive blend: roads high on both axes
    // score highest; roads low on either are penalised — which is the desired behaviour.
    val cqiExpr    = s"0.2 + ${wI.toString} * cqi_quality"
    val scenicExpr = s"0.2 + ${wS.toString} * scenic_quality"
    val requestModel = CustomModel()
      .addToPriority(Statement.If("true", Op.MULTIPLY, cqiExpr))
      .addToPriority(Statement.If("true", Op.MULTIPLY, scenicExpr))
    val req = GHRequest(start.lat, start.lon, end.lat, end.lon)
      .setProfile(Router.ProfileName)
      .setCustomModel(requestModel)
      .setPathDetails(util.List.of("cqi_quality", "scenic_quality"))
    val rsp: GHResponse = gh.route(req)
    if rsp.hasErrors() then
      val errors = rsp.getErrors().nn
      val msg =
        if errors.isEmpty then "unknown routing error"
        else errors.get(0).nn.getMessage.nn
      Left[RouteError, Route](RouteError.RoutingError(msg))
    else
      val path   = rsp.getBest().nn
      val ptList = path.getPoints().nn
      val points =
        (0 until ptList.size()).map(i => LatLon(ptList.getLat(i), ptList.getLon(i))).toList
      val detailMap = path.getPathDetails().nn
      val cqiDetails: List[PathDetail] =
        Option(detailMap.get("cqi_quality")).fold(List.empty[PathDetail])(_.nn.asScala.toList)
      val scenicDetails: List[PathDetail] =
        Option(detailMap.get("scenic_quality")).fold(List.empty[PathDetail])(_.nn.asScala.toList)
      Right[RouteError, Route](
        Route(
          distanceMeters = path.getDistance(),
          points         = points,
          cqiDetails     = cqiDetails,
          scenicDetails  = scenicDetails
        )
      )

object Router:
  private val ProfileName = "bike"

  /** Build / load a router from an OSM XML or PBF file, caching the graph to `graphCache`.
    * Scenic and infrastructure quality scores are loaded from `scoreFile`; if the file is
    * missing or unreadable, scores default to zero (graceful degradation).
    */
  def fromOsm(osmFile: Path, graphCache: Path, scoreFile: Path): Router =
    val scores = ScoreStore.load(scoreFile)
    new Router(buildHopper(osmFile.toString, graphCache.toString, scores))

  private def buildHopper(
      osmFile: String,
      graphCacheDir: String,
      scores: Map[Long, WayQuality]
  ): GraphHopper =
    // GH 11.0 dropped the `fastest` weighting; custom model is required.
    // Speed cap stays in the profile model; priority blend is applied per-request.
    val customModel = CustomModel()
      .addToSpeed(Statement.If("true", Op.LIMIT, "15"))
    val profile   = Profile(ProfileName).setCustomModel(customModel)
    val lmProfile = LMProfile(ProfileName)
    // graph.location and datareader.file must be in the config before init()
    val config = GraphHopperConfig()
      .putObject("graph.location", graphCacheDir)
      .putObject("datareader.file", osmFile)
      // GH 11.0 requires this parameter; motorway/trunk excluded for bike routing
      .putObject("import.osm.ignored_highways", util.List.of("motorway", "trunk"))
      // Register custom encoded values so the import registry can wire tag parsers
      .putObject("graph.encoded_values", "cqi_quality,scenic_quality")
      .setProfiles(util.List.of(profile))
      .setLMProfiles(util.List.of(lmProfile))
    val gh = GraphHopper()
    // setImportRegistry must be called BEFORE init()
    gh.setImportRegistry(ScenicImportRegistry(scores))
    gh.init(config)
    gh.importOrLoad()
    gh
