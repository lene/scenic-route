package scenicroute

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.{DecimalEncodedValue, EdgeIntAccess}
import com.graphhopper.routing.util.parsers.TagParser
import com.graphhopper.storage.IntsRef

/**
 * Writes a precomputed per-way quality score into a single [[DecimalEncodedValue]]
 * edge attribute during the GraphHopper OSM import phase.
 *
 * @param ev     the encoded-value slot that receives the score
 * @param scores map from OSM way ID to precomputed [[WayQuality]]
 * @param pick   selects the relevant quality dimension from [[WayQuality]]
 */
final class ScenicTagParser(
    ev: DecimalEncodedValue,
    scores: Map[Long, WayQuality],
    pick: WayQuality => Double
) extends TagParser:

  /** Sets the forward-direction EV for this edge; missing ways default to 0.0. */
  override def handleWayTags(
      edgeId: Int,
      access: EdgeIntAccess,
      way: ReaderWay,
      relationFlags: IntsRef
  ): Unit =
    val value = scores.get(way.getId).fold(0.0)(pick)
    ev.setDecimal(false, edgeId, access, value)
