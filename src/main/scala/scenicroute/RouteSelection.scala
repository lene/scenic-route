package scenicroute

object RouteSelection:

  def filterByDistance(routes: Seq[Route], targetM: Double, params: RouteParams): Seq[Route] =
    val lo = params.distanceToleranceLow * targetM
    val hi = params.distanceToleranceHigh * targetM
    routes.filter(r => r.distanceMeters >= lo && r.distanceMeters <= hi)

  def blendedScore(route: Route, params: RouteParams): Double =
    params.infraWeight * route.meanCqiQuality + params.scenicWeight * route.meanScenicQuality

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
    val scored  = routes.map(r => RankedRoute(r, blendedScore(r, params)))
    val sorted  = scored.sortBy(-_.blendedScore).toList
    val deduped = dedupe(sorted, params.overlapThreshold)
    deduped.take(math.min(params.numSuggestions, 5))
