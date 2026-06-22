package scenicroute

// ponytail: gradient seam, no elevation EV in v1, wire real DEM in v2
final case class RouteParams(
    infraWeight: Double,
    scenicWeight: Double,
    gradientWeight: Double
)

object RouteParams:
  val default: RouteParams = RouteParams(
    infraWeight = 0.5,
    scenicWeight = 0.5,
    gradientWeight = 0.0
  )
