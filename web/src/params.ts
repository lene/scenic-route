// Pure mapping from the sidebar's "essentials" controls to the backend ParamsDto.
// The backend (Api.toParams) validates: weights in [0,1]; low in (0,1]; high >= 1;
// low <= high; suggestions in [1,5]. This mapping is constructed to always satisfy them.

export interface ParamsDto {
  infraWeight: number
  scenicWeight: number
  distanceToleranceLow: number
  distanceToleranceHigh: number
  numSuggestions: number
}

export interface Controls {
  balance: number // 0 = all infra, 1 = all scenic
  tolerancePct: number // fraction; band is target * [1-p, 1+p]
  numSuggestions: number // 1..5
}

const clamp = (x: number, lo: number, hi: number): number => Math.min(hi, Math.max(lo, x))

export function toParamsDto(c: Controls): ParamsDto {
  const b = clamp(c.balance, 0, 1)
  const p = clamp(c.tolerancePct, 0, 0.9) // cap keeps low = 1-p strictly > 0
  return {
    infraWeight: 1 - b,
    scenicWeight: b,
    distanceToleranceLow: 1 - p,
    distanceToleranceHigh: 1 + p,
    numSuggestions: clamp(Math.round(c.numSuggestions), 1, 5),
  }
}
