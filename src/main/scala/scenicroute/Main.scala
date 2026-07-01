package scenicroute

import java.nio.file.{Files, Path, Paths}

/** A parsed command line: area config + start/end/target for a routing run. */
final case class RouteRequest(areaToml: String, start: LatLon, end: LatLon, targetKm: Double)

// ponytail: Any suppressed here — println in a CLI entry point inherently widens to Any
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object Main:

  /** Parse a `lat,lon` pair; `None` if malformed or out of geographic range. */
  def parseLatLon(s: String): Option[LatLon] =
    s.split(',').toList match
      case latS :: lonS :: Nil =>
        for
          lat <- latS.trim.nn.toDoubleOption
          if lat >= -90.0 && lat <= 90.0
          lon <- lonS.trim.nn.toDoubleOption
          if lon >= -180.0 && lon <= 180.0
        yield LatLon(lat, lon)
      case _ => None

  /** Parse `<areaToml> <lat,lon> <lat,lon> <targetKm>`; `None` if it doesn't fit. */
  def parseArgs(args: Array[String]): Option[RouteRequest] =
    args.toList match
      case toml :: startS :: endS :: kmS :: Nil =>
        for
          start <- parseLatLon(startS)
          end   <- parseLatLon(endS)
          km    <- kmS.toDoubleOption
          if km > 0.0
        yield RouteRequest(toml, start, end, km)
      case _ => None

  def main(args: Array[String]): Unit =
    parseArgs(args) match
      case Some(req) => runExport(req)
      case None =>
        if args.sizeIs > 1 then
          println("Could not parse routing args.")
          println(
            "Usage: <areaToml> <startLat,startLon> <endLat,endLon> <targetKm> — falling back to demo.\n"
          )
        runDemo(if args.sizeIs > 0 then args(0) else "areas/berlin.toml")

  /** CLI path: route to the requested target and write the export files. */
  private def runExport(req: RouteRequest): Unit =
    val cfg = AreaConfig.load(Paths.get(req.areaToml))
    printHeader(cfg)
    val router =
      Router.fromOsm(Paths.get(cfg.pbfFile), Paths.get(cfg.graphCache), Paths.get(cfg.scoresFile))
    println("Graph ready.")
    // isLoop: same abs-tolerance coordinate check as Router (== on Double is a wart).
    val loop = (req.start.lat - req.end.lat).abs < 1e-9 && (req.start.lon - req.end.lon).abs < 1e-9
    val mode = if loop then "loop" else "a2b"
    val routes = router.routeWithTarget(req.start, req.end, req.targetKm, RouteParams.default)
    printRanked(s"$mode ranked routes (target ${req.targetKm} km)", routes)
    if routes.isEmpty then println("Nothing landed in the tolerance window — no files written.")
    else
      // ponytail: whole-km label in the filename; fractional targets truncate in the
      // name only (the routed data is unaffected).
      writeExports(cfg.id, s"$mode-${req.targetKm.toInt}km", routes).foreach(p =>
        println(s"Wrote $p")
      )

  /** No / invalid routing args: the built-in Berlin demo, now also writing exports. */
  private def runDemo(areaToml: String): Unit =
    val cfg = AreaConfig.load(Paths.get(areaToml))
    printHeader(cfg)
    println(s"Demo: ${cfg.demoStart} → ${cfg.demoEnd}")
    val router =
      Router.fromOsm(Paths.get(cfg.pbfFile), Paths.get(cfg.graphCache), Paths.get(cfg.scoresFile))
    println("Graph ready.")
    val stockParams =
      RouteParams.default.copy(infraWeight = 0.0, scenicWeight = 0.0, gradientWeight = 0.0)
    val scenicParams = RouteParams.default

    def printRoute(label: String, params: RouteParams): Unit =
      print(s"[$label] Routing... ")
      router.route(cfg.demoStart, cfg.demoEnd, params) match
        case Right(r) =>
          println(
            s"${r.distanceMeters.toInt} m | cqi_quality=${f"${r.meanCqiQuality}%.3f"} scenic_quality=${f"${r.meanScenicQuality}%.3f"}"
          )
        case Left(e) =>
          println(s"FAILED: ${e.errorMessage}")

    printRoute("stock (wI=0, wS=0)", stockParams)
    printRoute("scenic (wI=0.5, wS=0.5)", scenicParams)

    println("\n--- Distance-target A→B demo (target 30 km) ---")
    val abRoutes = router.routeWithTarget(cfg.demoStart, cfg.demoEnd, 30.0, scenicParams)
    printRanked("A→B ranked routes", abRoutes)
    if abRoutes.nonEmpty then
      writeExports(cfg.id, "a2b-30km", abRoutes).foreach(p => println(s"Wrote $p"))

    println("\n--- Loop demo (target 20 km from start) ---")
    val loopRoutes = router.routeWithTarget(cfg.demoStart, cfg.demoStart, 20.0, scenicParams)
    printRanked("Loop ranked routes", loopRoutes)
    if loopRoutes.nonEmpty then
      writeExports(cfg.id, "loop-20km", loopRoutes).foreach(p => println(s"Wrote $p"))

  private def printHeader(cfg: AreaConfig): Unit =
    println(
      s"Area: ${cfg.id} (boundary relation ${cfg.boundaryRelationId}, buffer ${cfg.bufferKm} km)"
    )
    println(s"PBF:  ${cfg.pbfFile}")
    println(s"Graph cache: ${cfg.graphCache}")
    println("Building / loading GraphHopper graph (this may take a few minutes on first run)...")

  private def printRanked(label: String, results: Seq[RankedRoute]): Unit =
    println(s"\n$label (${results.size} routes):")
    if results.isEmpty then println("  (none in tolerance window)")
    else
      results.zipWithIndex.foreach: (rr, i) =>
        println(
          f"  ${i + 1}. ${rr.route.distanceMeters.toInt} m | score=${rr.blendedScore}%.3f | cqi=${rr.route.meanCqiQuality}%.3f | scenic=${rr.route.meanScenicQuality}%.3f"
        )

  /** Write the combined GeoJSON + GPX for a route set under `out/<area>/`. */
  private def writeExports(areaId: String, base: String, routes: Seq[RankedRoute]): List[Path] =
    val dir = Paths.get("out", areaId)
    val _   = Files.createDirectories(dir)
    val geo = dir.resolve(s"$base.geojson")
    val gpx = dir.resolve(s"$base.gpx")
    val _   = Files.writeString(geo, RouteExport.toGeoJson(routes))
    val _   = Files.writeString(gpx, RouteExport.toGpx(routes, s"$areaId $base"))
    List(geo, gpx)
