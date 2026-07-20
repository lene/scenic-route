package scenicroute

import com.graphhopper.util.DistanceCalcEarth

object RouteSelection:

  // Reused for segment lengths in doubledFraction; DistanceCalcEarth is on the
  // classpath via graphhopper-core, so we don't hand-roll haversine.
  private val distCalc = DistanceCalcEarth()

  def filterByDistance(routes: Seq[Route], targetM: Double, params: RouteParams): Seq[Route] =
    val lo = params.distanceToleranceLow * targetM
    val hi = params.distanceToleranceHigh * targetM
    routes.filter(r => r.distanceMeters >= lo && r.distanceMeters <= hi)

  /** Length-weighted fraction of a route ridden on segments traversed 2+ times (counting every
    * pass): 0.0 = clean loop, 1.0 = pure out-and-back. Derived from `points` alone, since a Route
    * carries no edge/way IDs. Endpoints are snapped to a ~1e-5° (~1 m) grid so distinct roads don't
    * collide, and each segment key is direction-normalised so out and back match.
    */
  def doubledFraction(route: Route): Double =
    val pts = route.points
    if pts.sizeIs < 2 then 0.0
    else
      val segs = pts.zip(pts.drop(1)).map { (a, b) =>
        val ka = (math.round(a.lat * 1e5), math.round(a.lon * 1e5))
        val kb = (math.round(b.lat * 1e5), math.round(b.lon * 1e5))
        // Direction-normalise the segment key using only `<` (Wart.Equals bans `==`).
        val key =
          if ka._1 < kb._1 then (ka, kb)
          else if kb._1 < ka._1 then (kb, ka)
          else if ka._2 < kb._2 then (ka, kb)
          else (kb, ka)
        val len = distCalc.calcDist(a.lat, a.lon, b.lat, b.lon)
        (key, len)
      }
      val total = segs.map(_._2).sum
      if total <= 0.0 then 0.0
      else
        val doubled = segs.groupBy(_._1).values.filter(_.sizeIs >= 2).flatten.map(_._2).sum
        doubled / total

  def blendedScore(route: Route, params: RouteParams): Double =
    scoreWith(route, params, doubledFraction(route))

  // Takes a precomputed doubledFraction so rankAndSelect computes it once per route
  // rather than twice (once for the cap, once for the score).
  private def scoreWith(route: Route, params: RouteParams, doubled: Double): Double =
    val quality =
      params.infraWeight * route.meanCqiQuality + params.scenicWeight * route.meanScenicQuality
    quality * (1.0 - params.doubledPenaltyWeight * doubled)

  // Membership gate: the higher the penalty weight, the lower the tolerated doubling.
  // w=0 → 1.0 (keep everything); w=0.8 → 0.44; w=1 → 0.3.
  private def maxDoubled(penaltyWeight: Double): Double = 1.0 - 0.7 * penaltyWeight

  // ponytail: rounded-point Jaccard; swap for edge-id sets if too coarse
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def overlap(a: Route, b: Route): Double =
    def pointSet(r: Route): Set[(Long, Long)] =
      r.points.map(p => (math.round(p.lat * 1000), math.round(p.lon * 1000))).toSet
    val sa    = pointSet(a)
    val sb    = pointSet(b)
    val inter = sa.intersect(sb).size.toDouble
    val union = sa.union(sb).size.toDouble
    if union <= 0.0 then 0.0 else inter / union

  def dedupe(ranked: List[RankedRoute], threshold: Double): List[RankedRoute] =
    ranked
      .foldLeft(List.empty[RankedRoute]): (acc, candidate) =>
        val isDuplicate = acc.exists(k => overlap(k.route, candidate.route) > threshold)
        if isDuplicate then acc else candidate :: acc
      .reverse

  def rankAndSelect(routes: Seq[Route], params: RouteParams): List[RankedRoute] =
    val withFraction = routes.map(r => (r, doubledFraction(r)))
    val cap          = maxDoubled(params.doubledPenaltyWeight)
    val kept         = withFraction.filter(_._2 <= cap)
    // Never return an empty set just because every candidate doubles (dead-end starts).
    val pool    = if kept.isEmpty then withFraction else kept
    val scored  = pool.map((r, f) => RankedRoute(r, scoreWith(r, params, f)))
    val sorted  = scored.sortBy(-_.blendedScore).toList
    val deduped = dedupe(sorted, params.overlapThreshold)
    deduped.take(math.min(params.numSuggestions, 5))
