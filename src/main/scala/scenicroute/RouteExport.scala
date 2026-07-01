package scenicroute

import java.util.Locale

/** Pure serialisers for ranked routes → GeoJSON / GPX text.
  *
  * All numeric formatting is pinned to `Locale.ROOT`: the dev JVM default locale
  * is German, where `%.6f` would emit `13,377` and corrupt both formats.
  */
// Any: StringContext.s takes Any*, so every interpolation widens to Any. This
// module is entirely string assembly; suppress once here (as Main does).
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object RouteExport:

  // simplestyle-spec palette so geojson.io renders the ranked set in distinct
  // colours (rank 1 = red, descending). Length must stay in sync with `% 5`.
  private val Palette: Vector[String] =
    Vector("#e41a1c", "#377eb8", "#4daf4a", "#984ea3", "#ff7f00")

  private def fmt(spec: String, x: Double): String =
    String.format(Locale.ROOT, spec, Double.box(x)).nn

  private def coord(x: Double): String = fmt("%.6f", x)
  private def score(x: Double): String = fmt("%.4f", x)
  private def dist(x: Double): String  = fmt("%.1f", x)

  private def strokeFor(rank: Int): String =
    Palette.lift((rank - 1) % 5).getOrElse("#333333")

  private def escapeXml(s: String): String =
    s.replace("&", "&amp;").nn
      .replace("<", "&lt;").nn
      .replace(">", "&gt;").nn
      .replace("\"", "&quot;").nn
      .replace("'", "&apos;").nn

  /** One FeatureCollection holding all routes; each a LineString in [lon,lat]
    * order (RFC 7946) with rank/score properties + simplestyle stroke.
    */
  def toGeoJson(routes: Seq[RankedRoute]): String =
    val features = routes.zipWithIndex.map: (rr, i) =>
      val rank   = i + 1
      val coords = rr.route.points.map(p => s"[${coord(p.lon)},${coord(p.lat)}]").mkString(",")
      val props =
        s""""rank":$rank,""" +
          s""""distance_m":${dist(rr.route.distanceMeters)},""" +
          s""""blended_score":${score(rr.blendedScore)},""" +
          s""""mean_cqi":${score(rr.route.meanCqiQuality)},""" +
          s""""mean_scenic":${score(rr.route.meanScenicQuality)},""" +
          s""""stroke":"${strokeFor(rank)}","stroke-width":4"""
      s"""{"type":"Feature","properties":{$props},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
    s"""{"type":"FeatureCollection","features":[${features.mkString(",")}]}"""

  /** One `<gpx>` document with one `<trk>` per route. No `<ele>` — the v1 area is
    * flat and carries no DEM (zero-weight gradient seam, SPEC §7.5).
    */
  def toGpx(routes: Seq[RankedRoute], name: String): String =
    val safe = escapeXml(name)
    val trks = routes.zipWithIndex.map: (rr, i) =>
      val rank = i + 1
      val pts = rr.route.points
        .map(p => s"""      <trkpt lat="${coord(p.lat)}" lon="${coord(p.lon)}"/>""")
        .mkString("\n")
      s"""  <trk>
         |    <name>$safe #$rank (score ${score(rr.blendedScore)})</name>
         |    <trkseg>
         |$pts
         |    </trkseg>
         |  </trk>""".stripMargin
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<gpx version="1.1" creator="scenic-route" xmlns="http://www.topografix.com/GPX/1/1">
       |${trks.mkString("\n")}
       |</gpx>""".stripMargin
