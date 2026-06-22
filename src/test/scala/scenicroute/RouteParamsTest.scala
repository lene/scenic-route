package scenicroute

class RouteParamsTest extends munit.FunSuite:
  test("default weights are equal infraWeight and scenicWeight"):
    assertEquals(RouteParams.default.infraWeight, 0.5)
    assertEquals(RouteParams.default.scenicWeight, 0.5)

  test("default gradientWeight is zero (v1 seam)"):
    assertEquals(RouteParams.default.gradientWeight, 0.0)
