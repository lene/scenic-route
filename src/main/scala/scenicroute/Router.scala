package scenicroute

import com.graphhopper.config.{LMProfile, Profile}
import com.graphhopper.json.Statement
import com.graphhopper.json.Statement.Op
import com.graphhopper.util.CustomModel
import com.graphhopper.{GHRequest, GHResponse, GraphHopper, GraphHopperConfig}

import java.nio.file.Path
import java.util

final class Router private (gh: GraphHopper):

  def route(start: LatLon, end: LatLon): Either[RouteError, Route] =
    val req = GHRequest(start.lat, start.lon, end.lat, end.lon)
      .setProfile(Router.ProfileName)
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
      Right[RouteError, Route](Route(distanceMeters = path.getDistance(), points = points))

object Router:
  private val ProfileName = "bike"

  /** Build / load a router from an OSM XML or PBF file, caching the graph to `graphCache`. */
  def fromOsm(osmFile: Path, graphCache: Path): Router =
    new Router(buildHopper(osmFile.toString, graphCache.toString))

  private def buildHopper(osmFile: String, graphCacheDir: String): GraphHopper =
    // GH 11.0 dropped the `fastest` weighting; custom model is required.
    // Phase 0: minimal model — cap speed at 15 km/h (typical cycling).
    // Phase 2 will extend this with scenic_quality and gradient weights.
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
      .setProfiles(util.List.of(profile))
      .setLMProfiles(util.List.of(lmProfile))
    val gh = GraphHopper()
    gh.init(config)
    gh.importOrLoad()
    gh
