package scenicroute

class RouteParamsTest extends munit.FunSuite:
  test("default weights are equal infraWeight and scenicWeight"):
    assertEquals(RouteParams.default.infraWeight, 0.5)
    assertEquals(RouteParams.default.scenicWeight, 0.5)

  test("default gradientWeight is zero (v1 seam)"):
    assertEquals(RouteParams.default.gradientWeight, 0.0)

  test("default distanceToleranceLow is 0.85"):
    assertEquals(RouteParams.default.distanceToleranceLow, 0.85)

  test("default distanceToleranceHigh is 1.15"):
    assertEquals(RouteParams.default.distanceToleranceHigh, 1.15)

  test("default numSuggestions is 4"):
    assertEquals(RouteParams.default.numSuggestions, 4)

  test("default overlapThreshold is 0.7"):
    assertEquals(RouteParams.default.overlapThreshold, 0.7)
