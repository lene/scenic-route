package scenicroute

import org.tomlj.Toml

import java.nio.file.Path

final case class AreaConfig(
    id: String,
    description: String,
    boundaryRelationId: Long,
    adminLevel: Int,
    bufferKm: Int,
    pbfUrls: List[String],
    pbfFile: String,
    graphCache: String,
    demoStart: LatLon,
    demoEnd: LatLon
)

object AreaConfig:
  def load(path: Path): AreaConfig =
    val toml  = Toml.parse(path)
    val area  = toml.getTable("area")
    val bnd   = toml.getTable("boundary")
    val src   = toml.getTable("sources")
    val paths = toml.getTable("paths")
    val demo  = toml.getTable("demo")

    val pbfUrls: List[String] =
      val arr = src.getArray("pbf_urls")
      (0 until arr.size()).map(arr.getString).toList

    AreaConfig(
      id = area.getString("id").nn,
      description = area.getString("description").nn,
      boundaryRelationId = bnd.getLong("relation_id").nn.longValue(),
      adminLevel = bnd.getLong("admin_level").nn.intValue(),
      bufferKm = bnd.getLong("buffer_km").nn.intValue(),
      pbfUrls = pbfUrls,
      pbfFile = paths.getString("pbf_file").nn,
      graphCache = paths.getString("graph_cache").nn,
      demoStart = latLon(demo.getTable("start").nn),
      demoEnd = latLon(demo.getTable("end").nn)
    )

  private def latLon(t: org.tomlj.TomlTable): LatLon =
    LatLon(
      lat = t.getDouble("lat").nn.doubleValue(),
      lon = t.getDouble("lon").nn.doubleValue()
    )
