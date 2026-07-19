import { useCallback, useState } from 'react'
import MapView from './MapView'
import Sidebar, { type Active } from './Sidebar'
import { findRoutes, type GeoJson, type Point, type RouteDto } from './api'
import { toParamsDto } from './params'
import { downloadGpx } from './gpx'
import './App.css'

// ponytail: hardcoded Berlin centre (the one built area); expose via /health when multi-area lands.
const BERLIN: Point = { lat: 52.52, lon: 13.405 }

export default function App() {
  const [start, setStart] = useState<Point | null>(null)
  const [end, setEnd] = useState<Point | null>(null)
  const [active, setActive] = useState<Active>('start')
  const [loop, setLoop] = useState(false)
  const [targetKm, setTargetKm] = useState(25)
  const [balance, setBalance] = useState(0.5)
  const [tolerancePct, setTolerance] = useState(0.2)
  const [numSuggestions, setSuggestions] = useState(3)
  const [routes, setRoutes] = useState<RouteDto[]>([])
  const [geojson, setGeojson] = useState<GeoJson | null>(null)
  const [selectedRank, setSelectedRank] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Place the active point; after setting the start, advance to end (unless looping).
  const placeActive = useCallback(
    (pt: Point) => {
      if (active === 'start') {
        setStart(pt)
        if (!loop) setActive('end')
      } else {
        setEnd(pt)
      }
    },
    [active, loop],
  )

  // Stable: marker dragend handlers bind once at marker creation.
  const onDragStart = useCallback((pt: Point) => setStart(pt), [])
  const onDragEnd = useCallback((pt: Point) => setEnd(pt), [])

  const onSetLoop = (v: boolean) => {
    setLoop(v)
    if (v) setActive('start')
  }

  const onFind = useCallback(async () => {
    if (!start) return
    const dest = loop ? start : end
    if (!dest) return
    setLoading(true)
    setError(null)
    setSelectedRank(null)
    try {
      const params = toParamsDto({ balance, tolerancePct, numSuggestions })
      const resp = await findRoutes({ start, end: dest, targetKm, params })
      setRoutes(resp.routes)
      setGeojson(resp.geojson)
      if (resp.routes.length === 0) {
        setError('No routes found — try a larger tolerance or different points.')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'request failed')
      setRoutes([])
      setGeojson(null)
    } finally {
      setLoading(false)
    }
  }, [start, end, loop, targetKm, balance, tolerancePct, numSuggestions])

  return (
    <div className="app">
      <Sidebar
        start={start}
        end={end}
        active={active}
        loop={loop}
        targetKm={targetKm}
        balance={balance}
        tolerancePct={tolerancePct}
        numSuggestions={numSuggestions}
        routes={routes}
        selectedRank={selectedRank}
        loading={loading}
        error={error}
        onSetActive={setActive}
        onSetLoop={onSetLoop}
        onSetTargetKm={setTargetKm}
        onSetBalance={setBalance}
        onSetTolerance={setTolerance}
        onSetSuggestions={setSuggestions}
        onPickPlace={placeActive}
        onFind={onFind}
        onSelectRoute={setSelectedRank}
        onDownloadGpx={(r: RouteDto) => downloadGpx(`scenic-route-${r.rank}.gpx`, r.gpx)}
      />
      <div className="map">
        <MapView
          center={BERLIN}
          start={start}
          end={loop ? null : end}
          geojson={geojson}
          selectedRank={selectedRank}
          onMapClick={placeActive}
          onDragStart={onDragStart}
          onDragEnd={onDragEnd}
        />
      </div>
    </div>
  )
}
