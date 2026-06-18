package scenicroute

final case class Route(distanceMeters: Double, points: List[LatLon])

enum RouteError:
  case NotFound(message: String)
  case RoutingError(message: String)

  def errorMessage: String = this match
    case NotFound(m)     => m
    case RoutingError(m) => m
