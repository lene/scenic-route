package scenicroute

import java.nio.file.Path
import scala.io.Source
import scala.util.{Try, Using}

/** Quality signals derived from Phase 1 CSV columns for a single OSM way. */
final case class WayQuality(
  /** Blended cycling-quality index: road surface + traffic stress, 0..1. */
  cqiQuality: Double,
  /** Blended scenic quality: green + blue feature proximity, 0..1. */
  scenicQuality: Double,
)

object ScoreStore:

  /**
   * Derives [[WayQuality]] from raw Phase 1 CSV signals.
   *
   * Sub-blends are fixed coefficients chosen to mirror the Phase 1 blend.py weighting:
   *   - cqiQuality  = 0.7 * (cqi / 100) + 0.3 * ((4 - lts) / 3)
   *   - scenicQuality = 0.5 * green + 0.5 * blue
   *
   * @param cqi   Cycling Quality Index (0..100)
   * @param lts   Level of Traffic Stress (1..4, lower is better)
   * @param green Green-feature proximity (0..1)
   * @param blue  Blue/water-feature proximity (0..1)
   */
  def deriveSignals(cqi: Double, lts: Int, green: Double, blue: Double): WayQuality =
    val cqiQuality    = 0.7 * (cqi / 100.0) + 0.3 * ((4 - lts.toDouble) / 3.0)
    val scenicQuality = 0.5 * green + 0.5 * blue
    WayQuality(cqiQuality = cqiQuality, scenicQuality = scenicQuality)

  /**
   * Loads the Phase 1 score CSV at [[path]] and returns an immutable [[Map]] from
   * OSM way ID to [[WayQuality]].
   *
   * Expected CSV columns (header required): `way_id,cqi,lts,green,blue,score`
   * The `score` column is present but ignored; only raw components are used.
   *
   * Returns [[Map.empty]] if the file does not exist or cannot be read, rather than
   * propagating an exception (functional error-handling policy).
   */
  def load(path: Path): Map[Long, WayQuality] =
    val tryResult: Try[Map[Long, WayQuality]] =
      Using(Source.fromFile(path.toFile.nn)) { source =>
        source
          .getLines()
          .drop(1) // skip header
          .flatMap(parseLine)
          .toMap
      }
    tryResult.getOrElse(Map.empty[Long, WayQuality])

  /** Parses one CSV data line into an optional (wayId, WayQuality) pair. */
  private def parseLine(line: String): Option[(Long, WayQuality)] =
    line.split(",", -1) match
      case Array(wayIdStr, cqiStr, ltsStr, greenStr, blueStr, _*) =>
        for
          wayId <- wayIdStr.trim.toLongOption
          cqi   <- cqiStr.trim.toDoubleOption
          lts   <- ltsStr.trim.toIntOption
          green <- greenStr.trim.toDoubleOption
          blue  <- blueStr.trim.toDoubleOption
        yield (wayId, deriveSignals(cqi = cqi, lts = lts, green = green, blue = blue))
      case _ => None
