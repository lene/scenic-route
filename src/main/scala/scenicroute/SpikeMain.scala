package scenicroute

import java.nio.file.Paths
import scala.math.{cos, sqrt, Pi}

// Throwaway spike for Phase 3 Milestone A — delete after Checkpoint 3a sign-off.
@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
object SpikeMain:
  val Start   = LatLon(52.5163, 13.3777) // Brandenburg Gate
  val End     = LatLon(52.4275, 13.6517) // Müggelsee
  val Direct  = 22768.0                  // m, from Phase 2 e2e

  def main(args: Array[String]): Unit =
    println("Loading router from Berlin graph (already built)...")
    val router = Router.fromOsm(
      Paths.get("data/berlin-80km.osm.pbf"),
      Paths.get("graph-cache/berlin"),
      Paths.get("data/berlin-scores.csv")
    )
    println(s"Router ready. Direct A→B = ${Direct/1000}%.1f km\n")

    val params = RouteParams.default

    // ── round_trip (loop from Start) ──────────────────────────────────────────
    println("=== ROUND_TRIP loops from Start ===")
    for targetKm <- List(15.0, 20.0, 30.0) do
      val targetM = targetKm * 1000
      val low = 0.85 * targetM; val high = 1.15 * targetM
      val results = (0 until 10).flatMap(seed =>
        router.routeLoop(Start, targetM, seed.toLong, params).map(d => (seed, d))
      ).toList
      val inWindow = results.count((_, d) => d >= low && d <= high)
      println(f"  target=${targetKm}%.0f km: ${results.size}/10 returned, ${inWindow}/${results.size} in [${low/1000}%.1f,${high/1000}%.1f] km")
      results.foreach((seed, d) =>
        val flag = if d >= low && d <= high then "✓" else "✗"
        println(f"    seed=$seed: ${d/1000}%.2f km $flag")
      )
    println()

    // ── via-point A→B ──────────────────────────────────────────────────────────
    println("=== VIA-POINT A→B sampling ===")
    for targetKm <- List(30.0, 40.0) do
      val targetM = targetKm * 1000
      val low = 0.85 * targetM; val high = 1.15 * targetM
      val vias = computeVias(Start, End, targetM)
      val results = vias.flatMap(via =>
        router.routeVia(Start, via, End, params).map(d => (via, d))
      )
      val inWindow = results.count((_, d) => d >= low && d <= high)
      println(f"  target=${targetKm}%.0f km (${vias.size} vias): ${inWindow}/${results.size} in [${low/1000}%.1f,${high/1000}%.1f] km")
      results.foreach((via, d) =>
        val flag = if d >= low && d <= high then "✓" else "✗"
        println(f"    via=(${via.lat}%.4f,${via.lon}%.4f): ${d/1000}%.2f km $flag")
      )
    println()

  // Place vias on both sides of AB perpendicular bisector at fractions of h.
  // h = sqrt((T/2)^2 - (D/2)^2) where T=target, D=direct (straight-line distance).
  private def computeVias(a: LatLon, b: LatLon, targetM: Double): List[LatLon] =
    val latScale = 111320.0
    val lonScale = 111320.0 * cos(a.lat * Pi / 180.0)
    val midLat = (a.lat + b.lat) / 2
    val midLon = (a.lon + b.lon) / 2
    // AB vector in metres
    val dyM = (b.lat - a.lat) * latScale
    val dxM = (b.lon - a.lon) * lonScale
    val dM  = sqrt(dyM*dyM + dxM*dxM)
    // perpendicular unit vector (normalise AB, rotate 90°)
    val pyU =  dxM / dM  // perp: (dx, -dy) normalised → but in lat/lon swap:
    val pxU = -dyM / dM  // perpendicular in lon direction
    // max offset
    val T2 = targetM / 2; val D2 = dM / 2
    val hMax = if T2 <= D2 then 0.0 else sqrt(T2*T2 - D2*D2)
    if hMax <= 0.0 then List(LatLon(midLat, midLon)) // target ≤ direct, no detour needed
    else
      // sample fractions of hMax on both sides of the perpendicular bisector
      val fracs = List(0.40, 0.55, 0.70, 0.85, 1.00)
      fracs.flatMap(f =>
        val h = hMax * f
        List(
          LatLon(midLat + h * pyU / latScale, midLon + h * pxU / lonScale),
          LatLon(midLat - h * pyU / latScale, midLon - h * pxU / lonScale)
        )
      )
