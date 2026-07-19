import { describe, it, expect, vi, afterEach } from 'vitest'
import { findRoutes, geocode, type RouteReq } from './api'

afterEach(() => vi.unstubAllGlobals())

function mockFetch(body: unknown, ok = true, status = 200, text = '') {
  const res = { ok, status, statusText: 'x', json: async () => body, text: async () => text }
  const f = vi.fn(async () => res as unknown as Response)
  vi.stubGlobal('fetch', f)
  return f
}

const req: RouteReq = {
  start: { lat: 1, lon: 2 },
  end: { lat: 3, lon: 4 },
  targetKm: 20,
  params: {
    infraWeight: 0.5,
    scenicWeight: 0.5,
    distanceToleranceLow: 0.8,
    distanceToleranceHigh: 1.2,
    numSuggestions: 3,
  },
}

describe('findRoutes', () => {
  it('POSTs the request as JSON and returns the parsed body', async () => {
    const resp = { geojson: { type: 'FeatureCollection', features: [] }, routes: [] }
    const f = mockFetch(resp)
    const out = await findRoutes(req)
    expect(out).toEqual(resp)
    const [url, init] = f.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toContain('/routes')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual(req)
  })

  it('throws the server error text on a non-ok response', async () => {
    mockFetch(null, false, 400, 'targetKm must be > 0')
    await expect(findRoutes(req)).rejects.toThrow('targetKm must be > 0')
  })
})

describe('geocode', () => {
  it('GETs /geocode with an encoded query and returns results', async () => {
    const results = [{ label: 'Brandenburger Tor', lat: 52.5, lon: 13.3 }]
    const f = mockFetch(results)
    const out = await geocode('Brandenburger Tor')
    expect(out).toEqual(results)
    const [url] = f.mock.calls[0] as unknown as [string]
    expect(url).toContain('/geocode?q=Brandenburger%20Tor')
  })
})
