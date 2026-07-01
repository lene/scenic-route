package scenicroute

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.CORS
import org.http4s.{Header, Method, Request}
import org.typelevel.ci.CIString

import java.nio.file.Paths

/** Composition root: loads one area's graph once, exposes the routing + geocode API over
  * http4s/ember. Config via env: SCENIC_AREA, SCENIC_HOST, SCENIC_PORT.
  */
// Any: ember/http4s/tapir builders surface Any in inferred types; the wiring is
// otherwise fully typed. (Only unit-testable pieces live in Api/Routes/Geocode.)
@SuppressWarnings(Array("org.wartremover.warts.Any"))
object Server extends IOApp.Simple:

  private def env(key: String, default: String): String = sys.env.getOrElse(key, default)

  /** Live Nominatim geocoder (proper User-Agent per their usage policy). */
  private def liveGeocode(client: Client[IO]): Routes.GeocodeFn = q =>
    val uri = uri"https://nominatim.openstreetmap.org/search"
      .withQueryParam("q", q)
      .withQueryParam("format", "jsonv2")
      .withQueryParam("limit", 5)
    val req = Request[IO](Method.GET, uri).putHeaders(
      Header.Raw(
        CIString("User-Agent"),
        "scenic-route/0.1 (personal; +github.com/lene/scenic-route)"
      )
    )
    // fail soft: a geocoder hiccup returns no matches rather than a 500
    client.expect[String](req).map(Geocode.parseNominatim).handleError(_ => Nil)

  val run: IO[Unit] =
    val areaToml = env("SCENIC_AREA", "areas/berlin.toml")
    val h        = Host.fromString(env("SCENIC_HOST", "0.0.0.0")).getOrElse(host"0.0.0.0")
    val p = env("SCENIC_PORT", "8080").toIntOption.flatMap(Port.fromInt).getOrElse(port"8080")
    for
      _   <- IO.println(s"Loading area $areaToml (graph builds on first run)...")
      cfg <- IO.blocking(AreaConfig.load(Paths.get(areaToml)))
      router <- IO.blocking(
        Router.fromOsm(Paths.get(cfg.pbfFile), Paths.get(cfg.graphCache), Paths.get(cfg.scoresFile))
      )
      _ <- IO.println(s"Graph ready. Serving $h:$p (area ${cfg.id}).")
      _ <- EmberClientBuilder.default[IO].build.use { client =>
        val routeFn: Routes.RouteFn =
          (s, e, km, params) => router.routeWithTarget(s, e, km, params)
        // ponytail: allow-all CORS; restrict via withAllowOriginHost once deployed to a real origin.
        val app = CORS.policy.withAllowOriginAll(
          Routes.httpRoutes(routeFn, liveGeocode(client)).orNotFound
        )
        EmberServerBuilder
          .default[IO]
          .withHost(h)
          .withPort(p)
          .withHttpApp(app)
          .build
          .useForever
          .void
      }
    yield ()
