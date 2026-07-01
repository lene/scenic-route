package scenicroute

import java.nio.file.Paths

// ponytail: Any suppressed here — println in a CLI entry point inherently widens to Any
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object Main:
  def main(args: Array[String]): Unit =
    val areaToml = if args.sizeIs > 0 then args(0) else "areas/berlin.toml"
    val cfg      = AreaConfig.load(Paths.get(areaToml))
    println(
      s"Area: ${cfg.id} (boundary relation ${cfg.boundaryRelationId}, buffer ${cfg.bufferKm} km)"
    )
    println(s"PBF:  ${cfg.pbfFile}")
    println(s"Graph cache: ${cfg.graphCache}")
    println(s"Demo: ${cfg.demoStart} → ${cfg.demoEnd}")
    println("Building / loading GraphHopper graph (this may take a few minutes on first run)...")
    val router = Router.fromOsm(Paths.get(cfg.pbfFile), Paths.get(cfg.graphCache), Paths.get(cfg.scoresFile))
    println("Graph ready.")
    val stockParams  = RouteParams.default.copy(infraWeight = 0.0, scenicWeight = 0.0, gradientWeight = 0.0)
    val scenicParams = RouteParams.default

    def printRoute(label: String, params: RouteParams): Unit =
      print(s"[$label] Routing... ")
      router.route(cfg.demoStart, cfg.demoEnd, params) match
        case Right(r) =>
          println(s"${r.distanceMeters.toInt} m | cqi_quality=${f"${r.meanCqiQuality}%.3f"} scenic_quality=${f"${r.meanScenicQuality}%.3f"}")
        case Left(e) =>
          println(s"FAILED: ${e.errorMessage}")

    printRoute("stock (wI=0, wS=0)", stockParams)
    printRoute("scenic (wI=0.5, wS=0.5)", scenicParams)

    def printRanked(label: String, results: Seq[RankedRoute]): Unit =
      println(s"\n$label (${results.size} routes):")
      if results.isEmpty then println("  (none in tolerance window)")
      else
        results.zipWithIndex.foreach: (rr, i) =>
          println(f"  ${i + 1}. ${rr.route.distanceMeters.toInt} m | score=${rr.blendedScore}%.3f | cqi=${rr.route.meanCqiQuality}%.3f | scenic=${rr.route.meanScenicQuality}%.3f")

    // A→B distance-target demo: Brandenburg Gate → Müggelsee, target 30 km
    println("\n--- Distance-target A→B demo (target 30 km) ---")
    val abRoutes = router.routeWithTarget(cfg.demoStart, cfg.demoEnd, 30.0, scenicParams)
    printRanked("A→B ranked routes", abRoutes)

    // Loop demo: from start point, target 20 km
    println("\n--- Loop demo (target 20 km from start) ---")
    val loopRoutes = router.routeWithTarget(cfg.demoStart, cfg.demoStart, 20.0, scenicParams)
    printRanked("Loop ranked routes", loopRoutes)
