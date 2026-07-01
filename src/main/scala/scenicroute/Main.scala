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
