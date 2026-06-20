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
    val router = Router.fromOsm(Paths.get(cfg.pbfFile), Paths.get(cfg.graphCache))
    println("Graph ready. Routing...")
    router.route(cfg.demoStart, cfg.demoEnd) match
      case Right(route) =>
        println(s"Route found: ${route.distanceMeters.toInt} m, ${route.points.size} points")
        route.points.headOption.foreach(p => println(s"  Start: $p"))
        route.points.lastOption.foreach(p => println(s"  End:   $p"))
      case Left(err) =>
        println(s"Routing failed: ${err.errorMessage}")
