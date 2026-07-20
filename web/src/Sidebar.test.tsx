import { describe, it, expect, vi } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import Sidebar, { type Active } from './Sidebar'
import type { Point } from './api'

const noop = () => {}

function props(over: Partial<Parameters<typeof Sidebar>[0]> = {}) {
  return {
    start: null as Point | null,
    end: null as Point | null,
    active: 'start' as Active,
    loop: false,
    targetKm: 25,
    balance: 0.5,
    tolerancePct: 0.2,
    numSuggestions: 3,
    avoidBacktracking: 0.8,
    routes: [],
    selectedRank: null,
    loading: false,
    error: null,
    onSetActive: noop,
    onSetLoop: noop,
    onSetTargetKm: noop,
    onSetBalance: noop,
    onSetTolerance: noop,
    onSetSuggestions: noop,
    onSetAvoidBacktracking: noop,
    onPickPlace: noop,
    onFind: noop,
    onSelectRoute: noop,
    onDownloadGpx: noop,
    ...over,
  }
}

describe('Sidebar Find button', () => {
  it('is disabled with no start point', () => {
    render(<Sidebar {...props()} />)
    expect(screen.getByRole('button', { name: /find routes/i })).toBeDisabled()
    cleanup()
  })

  it('is enabled for a loop once a start is set (end not required)', () => {
    render(<Sidebar {...props({ start: { lat: 1, lon: 2 }, loop: true })} />)
    expect(screen.getByRole('button', { name: /find routes/i })).toBeEnabled()
    cleanup()
  })

  it('needs both points for a point-to-point ride', () => {
    render(<Sidebar {...props({ start: { lat: 1, lon: 2 }, loop: false })} />)
    expect(screen.getByRole('button', { name: /find routes/i })).toBeDisabled()
    cleanup()
  })

  it('calls onFind when clicked and enabled', () => {
    const onFind = vi.fn()
    render(<Sidebar {...props({ start: { lat: 1, lon: 2 }, loop: true, onFind })} />)
    screen.getByRole('button', { name: /find routes/i }).click()
    expect(onFind).toHaveBeenCalledOnce()
    cleanup()
  })
})
