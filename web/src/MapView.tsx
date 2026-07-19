import { useEffect, useRef, useState } from 'react'
import maplibregl from 'maplibre-gl'
import type { GeoJson, Point } from './api'

const OSM_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: 'raster',
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      attribution: '© OpenStreetMap contributors',
    },
  },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
}

const EMPTY_FC: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] }

interface Props {
  center: Point
  start: Point | null
  end: Point | null
  geojson: GeoJson | null
  selectedRank: number | null
  onMapClick: (p: Point) => void
  onDragStart: (p: Point) => void
  onDragEnd: (p: Point) => void
}

export default function MapView(props: Props) {
  const container = useRef<HTMLDivElement | null>(null)
  const map = useRef<maplibregl.Map | null>(null)
  const startMarker = useRef<maplibregl.Marker | null>(null)
  const endMarker = useRef<maplibregl.Marker | null>(null)
  const [ready, setReady] = useState(false)

  // Latest callbacks in a ref so the once-bound map click handler never goes stale.
  const clickCb = useRef(props.onMapClick)
  clickCb.current = props.onMapClick

  // Init the map exactly once.
  useEffect(() => {
    if (!container.current) return
    const m = new maplibregl.Map({
      container: container.current,
      style: OSM_STYLE,
      center: [props.center.lon, props.center.lat],
      zoom: 12,
    })
    m.on('load', () => {
      m.addSource('routes', { type: 'geojson', data: EMPTY_FC })
      m.addLayer({
        id: 'routes-line',
        type: 'line',
        source: 'routes',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': ['get', 'stroke'], 'line-width': 4, 'line-opacity': 0.9 },
      })
      setReady(true)
    })
    m.on('click', (e) => clickCb.current({ lat: e.lngLat.lat, lon: e.lngLat.lng }))
    map.current = m
    return () => {
      m.remove()
      map.current = null
      setReady(false)
    }
    // props.center is only the initial view; intentionally not a dependency.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Push new route geometry into the source.
  useEffect(() => {
    if (!ready || !map.current) return
    const src = map.current.getSource('routes') as maplibregl.GeoJSONSource | undefined
    src?.setData((props.geojson ?? EMPTY_FC) as unknown as GeoJSON.FeatureCollection)
  }, [props.geojson, ready])

  // Highlight the selected route; dim the rest while one is selected.
  useEffect(() => {
    if (!ready || !map.current) return
    const r = props.selectedRank
    map.current.setPaintProperty(
      'routes-line',
      'line-width',
      r == null ? 4 : ['case', ['==', ['get', 'rank'], r], 7, 3],
    )
    map.current.setPaintProperty(
      'routes-line',
      'line-opacity',
      r == null ? 0.9 : ['case', ['==', ['get', 'rank'], r], 1, 0.3],
    )
  }, [props.selectedRank, ready])

  // Sync draggable start/end markers to state.
  useEffect(() => {
    if (!ready || !map.current) return
    syncMarker(startMarker, map.current, props.start, '#2e7d32', props.onDragStart)
    syncMarker(endMarker, map.current, props.end, '#c62828', props.onDragEnd)
  }, [props.start, props.end, props.onDragStart, props.onDragEnd, ready])

  return <div ref={container} style={{ width: '100%', height: '100%' }} />
}

function syncMarker(
  ref: React.MutableRefObject<maplibregl.Marker | null>,
  map: maplibregl.Map,
  point: Point | null,
  color: string,
  onDragEnd: (p: Point) => void,
): void {
  if (!point) {
    ref.current?.remove()
    ref.current = null
    return
  }
  if (!ref.current) {
    const marker = new maplibregl.Marker({ color, draggable: true })
      .setLngLat([point.lon, point.lat])
      .addTo(map)
    // onDragEnd is stable (useCallback in App), so binding once at creation is safe.
    marker.on('dragend', () => {
      const l = marker.getLngLat()
      onDragEnd({ lat: l.lat, lon: l.lng })
    })
    ref.current = marker
  } else {
    ref.current.setLngLat([point.lon, point.lat])
  }
}
