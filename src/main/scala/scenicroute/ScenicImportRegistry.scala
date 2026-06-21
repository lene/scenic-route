package scenicroute

import com.graphhopper.routing.ev.{
  DecimalEncodedValueImpl,
  DefaultImportRegistry,
  EncodedValue,
  EncodedValueLookup,
  ImportRegistry,
  ImportUnit
}
import com.graphhopper.util.PMap

/**
 * A GraphHopper [[ImportRegistry]] that adds two custom encoded values —
 * `cqi_quality` and `scenic_quality` — backed by precomputed [[WayQuality]] scores.
 *
 * All other EV names are forwarded to a [[DefaultImportRegistry]] delegate so
 * standard GraphHopper attributes continue to work.
 *
 * @param scores map from OSM way ID to precomputed [[WayQuality]]
 */
final class ScenicImportRegistry(scores: Map[Long, WayQuality]) extends ImportRegistry:

  // Delegate for all non-scenic encoded values.
  private val delegate = DefaultImportRegistry()

  /**
   * Returns an [[ImportUnit]] for the named encoded value.
   *
   * `"cqi_quality"` and `"scenic_quality"` receive 7-bit, 0.01-resolution
   * [[DecimalEncodedValueImpl]] slots wired to a [[ScenicTagParser]].
   * Any other name is delegated to [[DefaultImportRegistry]].
   */
  override def createImportUnit(name: String): ImportUnit =
    name match
      case "cqi_quality" =>
        ImportUnit.create(
          "cqi_quality",
          (_: PMap) => DecimalEncodedValueImpl("cqi_quality", 7, 0.01, false): EncodedValue,
          (lookup: EncodedValueLookup, _: PMap) =>
            ScenicTagParser(
              lookup.getDecimalEncodedValue("cqi_quality").nn,
              scores,
              _.cqiQuality
            )
        )
      case "scenic_quality" =>
        ImportUnit.create(
          "scenic_quality",
          (_: PMap) => DecimalEncodedValueImpl("scenic_quality", 7, 0.01, false): EncodedValue,
          (lookup: EncodedValueLookup, _: PMap) =>
            ScenicTagParser(
              lookup.getDecimalEncodedValue("scenic_quality").nn,
              scores,
              _.scenicQuality
            )
        )
      case _ =>
        delegate.createImportUnit(name).nn
