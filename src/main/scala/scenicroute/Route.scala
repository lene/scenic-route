package scenicroute

import com.graphhopper.util.details.PathDetail

final case class Route(
    distanceMeters: Double,
    points: List[LatLon],
    cqiDetails: List[PathDetail],    // path details for mean EV computation
    scenicDetails: List[PathDetail]
):
  lazy val meanCqiQuality: Double    = meanDetail(cqiDetails)
  lazy val meanScenicQuality: Double = meanDetail(scenicDetails)

  // AsInstanceOf: PathDetail.getValue() returns Object | Null; GH stores DecimalEncodedValue
  // results as java.lang.Double, so the cast is guaranteed safe.
  // Any: .nn returns Object which is Any in Scala; unavoidable for the cast pattern.
  // Equals: use <= 0 to compare Int sum, avoiding the == operator.
  @SuppressWarnings(Array(
    "org.wartremover.warts.AsInstanceOf",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Equals"
  ))
  private def meanDetail(details: List[PathDetail]): Double =
    if details.isEmpty then 0.0
    else
      val total = details.foldLeft(0.0): (acc, d) =>
        acc + d.getValue.nn.asInstanceOf[Double] * d.getLength
      val len = details.foldLeft(0)((acc, d) => acc + d.getLength)
      if len <= 0 then 0.0 else total / len

enum RouteError:
  case NotFound(message: String)
  case RoutingError(message: String)

  def errorMessage: String = this match
    case NotFound(m)     => m
    case RoutingError(m) => m
