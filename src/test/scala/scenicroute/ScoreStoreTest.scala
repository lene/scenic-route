package scenicroute

import java.nio.file.Paths

// WartRemover flags `Any` in munit's assertEquals macro expansions; suppress for test files.
@SuppressWarnings(Array("org.wartremover.warts.Any"))
class ScoreStoreTest extends munit.FunSuite:

  // ── deriveSignals ────────────────────────────────────────────────────────────

  test("deriveSignals: busy primary road has low cqiQuality"):
    // cqi=12.8, lts=4, green=0.1, blue=0.05
    val wq = ScoreStore.deriveSignals(cqi = 12.8, lts = 4, green = 0.1, blue = 0.05)
    // cqiQuality = 0.7*(12.8/100) + 0.3*((4-4)/3) = 0.0896 + 0 = 0.0896
    assertEqualsDouble(wq.cqiQuality, 0.0896, delta = 1e-9)
    // scenicQuality = 0.5*0.1 + 0.5*0.05 = 0.075
    assertEqualsDouble(wq.scenicQuality, 0.075, delta = 1e-9)

  test("deriveSignals: forest path has high cqiQuality and high scenicQuality"):
    // cqi=80, lts=1, green=0.78, blue=0
    val wq = ScoreStore.deriveSignals(cqi = 80.0, lts = 1, green = 0.78, blue = 0.0)
    // cqiQuality = 0.7*(80/100) + 0.3*((4-1)/3) = 0.56 + 0.3 = 0.86
    assertEqualsDouble(wq.cqiQuality, 0.86, delta = 1e-9)
    // scenicQuality = 0.5*0.78 + 0.5*0 = 0.39
    assertEqualsDouble(wq.scenicQuality, 0.39, delta = 1e-9)

  // ── load ─────────────────────────────────────────────────────────────────────

  test("load: parses 3-row fixture CSV correctly"):
    val path = Paths.get(getClass.getResource("/fixtures/test-scores.csv").toURI.nn)
    val store = ScoreStore.load(path)

    assertEquals(store.size, 3)

    // Row 1: way_id=100, cqi=12.8, lts=4, green=0.1, blue=0.05
    val wq100 = store.getOrElse(100L, fail("way 100 not found"))
    assertEqualsDouble(wq100.cqiQuality, 0.0896, delta = 1e-9)
    assertEqualsDouble(wq100.scenicQuality, 0.075, delta = 1e-9)

    // Row 2: way_id=200, cqi=80, lts=1, green=0.78, blue=0
    val wq200 = store.getOrElse(200L, fail("way 200 not found"))
    assertEqualsDouble(wq200.cqiQuality, 0.86, delta = 1e-9)
    assertEqualsDouble(wq200.scenicQuality, 0.39, delta = 1e-9)

    // Row 3: way_id=300, cqi=50, lts=2, green=0.5, blue=0.5
    val wq300 = store.getOrElse(300L, fail("way 300 not found"))
    // cqiQuality = 0.7*(50/100) + 0.3*((4-2)/3) = 0.35 + 0.2 = 0.55
    assertEqualsDouble(wq300.cqiQuality, 0.55, delta = 1e-9)
    // scenicQuality = 0.5*0.5 + 0.5*0.5 = 0.5
    assertEqualsDouble(wq300.scenicQuality, 0.5, delta = 1e-9)

  test("load: returns empty map when file does not exist"):
    val missing = Paths.get("/tmp/this-file-does-not-exist-scenic-route.csv")
    val store   = ScoreStore.load(missing)
    assertEquals(store, Map.empty[Long, WayQuality])
