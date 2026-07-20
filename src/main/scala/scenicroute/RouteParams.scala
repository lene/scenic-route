package scenicroute

// ponytail: gradient seam, no elevation EV in v1, wire real DEM in v2
final case class RouteParams(
    infraWeight: Double,
    scenicWeight: Double,
    gradientWeight: Double,
    distanceToleranceLow: Double,
    distanceToleranceHigh: Double,
    numSuggestions: Int,
    overlapThreshold: Double,
    // Soft penalty for riding the same road out and back within one route:
    // score *= (1 - doubledPenaltyWeight * doubledFraction). 0 disables it.
    doubledPenaltyWeight: Double
)

object RouteParams:
  val default: RouteParams = RouteParams(
    infraWeight = 0.5,
    scenicWeight = 0.5,
    gradientWeight = 0.0,
    distanceToleranceLow = 0.85,
    distanceToleranceHigh = 1.15,
    numSuggestions = 4,
    overlapThreshold = 0.7,
    doubledPenaltyWeight = 0.8
  )
