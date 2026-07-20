import { describe, it, expect } from 'vitest'
import { toParamsDto, type Controls } from './params'

// Fill defaults so each test names only the controls it cares about.
const ctrl = (over: Partial<Controls> = {}): Controls => ({
  balance: 0.5,
  tolerancePct: 0,
  numSuggestions: 3,
  avoidBacktracking: 0.8,
  ...over,
})

describe('toParamsDto', () => {
  it('balance 0 = all infra', () => {
    const p = toParamsDto(ctrl({ balance: 0 }))
    expect(p.infraWeight).toBe(1)
    expect(p.scenicWeight).toBe(0)
  })

  it('balance 1 = all scenic', () => {
    const p = toParamsDto(ctrl({ balance: 1 }))
    expect(p.infraWeight).toBe(0)
    expect(p.scenicWeight).toBe(1)
  })

  it('maps tolerance pct to a symmetric band', () => {
    const p = toParamsDto(ctrl({ tolerancePct: 0.2 }))
    expect(p.distanceToleranceLow).toBeCloseTo(0.8)
    expect(p.distanceToleranceHigh).toBeCloseTo(1.2)
  })

  it('clamps tolerance so low stays > 0', () => {
    const p = toParamsDto(ctrl({ tolerancePct: 1.5 }))
    expect(p.distanceToleranceLow).toBeGreaterThan(0)
    expect(p.distanceToleranceLow).toBeLessThanOrEqual(1)
  })

  it('clamps and rounds numSuggestions to [1,5]', () => {
    expect(toParamsDto(ctrl({ numSuggestions: 9 })).numSuggestions).toBe(5)
    expect(toParamsDto(ctrl({ numSuggestions: 0 })).numSuggestions).toBe(1)
    expect(toParamsDto(ctrl({ numSuggestions: 2.6 })).numSuggestions).toBe(3)
  })

  it('maps avoidBacktracking to doubledPenaltyWeight, clamped to [0,1]', () => {
    expect(toParamsDto(ctrl({ avoidBacktracking: 0 })).doubledPenaltyWeight).toBe(0)
    expect(toParamsDto(ctrl({ avoidBacktracking: 0.8 })).doubledPenaltyWeight).toBeCloseTo(0.8)
    expect(toParamsDto(ctrl({ avoidBacktracking: 1.7 })).doubledPenaltyWeight).toBe(1)
    expect(toParamsDto(ctrl({ avoidBacktracking: -1 })).doubledPenaltyWeight).toBe(0)
  })

  it('always satisfies the backend band constraints', () => {
    const p = toParamsDto(ctrl({ balance: 0.3, tolerancePct: 0.25, numSuggestions: 4 }))
    expect(p.distanceToleranceLow).toBeGreaterThan(0)
    expect(p.distanceToleranceLow).toBeLessThanOrEqual(1)
    expect(p.distanceToleranceHigh).toBeGreaterThanOrEqual(1)
    expect(p.distanceToleranceLow).toBeLessThanOrEqual(p.distanceToleranceHigh)
  })
})
