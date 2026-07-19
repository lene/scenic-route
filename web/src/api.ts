// Typed client for the scenic-route backend (Server.scala). Same-origin in dev
// (Vite proxies /routes,/geocode,/health → :8080); prod uses VITE_API_BASE.

import type { ParamsDto } from './params'

export interface Point {
  lat: number
  lon: number
}

export interface RouteReq {
  start: Point
  end: Point
  targetKm: number
  params: ParamsDto
}

export interface RouteDto {
  rank: number
  distanceM: number
  blendedScore: number
  meanCqi: number
  meanScenic: number
  gpx: string
}

// A GeoJSON FeatureCollection; kept minimal so we need no @types/geojson dep.
export interface GeoJson {
  type: string
  features: unknown[]
}

export interface RouteResp {
  geojson: GeoJson
  routes: RouteDto[]
}

export interface GeoResult {
  label: string
  lat: number
  lon: number
}

const base = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')

async function errorText(res: Response): Promise<string> {
  const body = await res.text().catch(() => '')
  return body || `HTTP ${res.status}`
}

export async function findRoutes(req: RouteReq): Promise<RouteResp> {
  const res = await fetch(`${base}/routes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) throw new Error(await errorText(res))
  return (await res.json()) as RouteResp
}

export async function geocode(q: string): Promise<GeoResult[]> {
  const res = await fetch(`${base}/geocode?q=${encodeURIComponent(q)}`)
  if (!res.ok) throw new Error(await errorText(res))
  return (await res.json()) as GeoResult[]
}
