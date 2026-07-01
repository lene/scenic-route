package scenicroute

import com.graphhopper.util.details.PathDetail

final case class Route(
    distanceMeters: Double,
    points: List[LatLon],
    cqiDetails: List[PathDetail], // path details for mean EV computation
    scenicDetails: List[PathDetail]
):
  lazy val meanCqiQuality: Double    = meanDetail(cqiDetails)
  lazy val meanScenicQuality: Double = meanDetail(scenicDetails)

  // PathDetail.getValue() returns Object | Null; GH stores DecimalEncodedValue results as
  // java.lang.Double, matched here (scalafix disables asInstanceOf).
  // Any: .nn returns Object, which is Any in Scala; unavoidable when reading getValue.
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def meanDetail(details: List[PathDetail]): Double =
    if details.isEmpty then 0.0
    else
      val total = details.foldLeft(0.0): (acc, d) =>
        val v = d.getValue.nn match
          case x: java.lang.Double => x.doubleValue()
          case _                   => 0.0
        acc + v * d.getLength
      val len = details.foldLeft(0)((acc, d) => acc + d.getLength)
      if len <= 0 then 0.0 else total / len

enum RouteError:
  case NotFound(message: String)
  case RoutingError(message: String)

  def errorMessage: String = this match
    case NotFound(m)     => m
    case RoutingError(m) => m
