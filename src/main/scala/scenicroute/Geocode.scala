package scenicroute

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.{Decoder, Encoder}

final case class GeoResult(label: String, lat: Double, lon: Double)
object GeoResult:
  given Decoder[GeoResult] = deriveDecoder
  given Encoder[GeoResult] = deriveEncoder

object Geocode:

  /** Parse a Nominatim jsonv2 response (array of objects with string lat/lon). Malformed entries
    * are dropped; a non-array yields Nil.
    */
  def parseNominatim(body: String): List[GeoResult] =
    parse(body).toOption
      .flatMap(_.asArray)
      .getOrElse(Vector.empty[io.circe.Json])
      .flatMap { j =>
        val c = j.hcursor
        for
          label <- c.get[String]("display_name").toOption
          latS  <- c.get[String]("lat").toOption
          lonS  <- c.get[String]("lon").toOption
          lat   <- latS.toDoubleOption
          lon   <- lonS.toDoubleOption
        yield GeoResult(label, lat, lon)
      }
      .toList
