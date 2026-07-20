import { useState, type FormEvent } from 'react'
import { geocode, type GeoResult, type Point, type RouteDto } from './api'

// Mirrors RouteExport.Palette so the list swatch matches the map line colour.
const PALETTE = ['#e41a1c', '#377eb8', '#4daf4a', '#984ea3', '#ff7f00']
const strokeFor = (rank: number): string => PALETTE[(rank - 1) % PALETTE.length]

export type Active = 'start' | 'end'

interface Props {
  start: Point | null
  end: Point | null
  active: Active
  loop: boolean
  targetKm: number
  balance: number
  tolerancePct: number
  numSuggestions: number
  avoidBacktracking: number
  routes: RouteDto[]
  selectedRank: number | null
  loading: boolean
  error: string | null
  onSetActive: (a: Active) => void
  onSetLoop: (v: boolean) => void
  onSetTargetKm: (v: number) => void
  onSetBalance: (v: number) => void
  onSetTolerance: (v: number) => void
  onSetSuggestions: (v: number) => void
  onSetAvoidBacktracking: (v: number) => void
  onPickPlace: (p: Point) => void
  onFind: () => void
  onSelectRoute: (rank: number | null) => void
  onDownloadGpx: (route: RouteDto) => void
}

export default function Sidebar(p: Props) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState<GeoResult[]>([])
  const [searching, setSearching] = useState(false)
  const [searchErr, setSearchErr] = useState<string | null>(null)

  async function search(e: FormEvent) {
    e.preventDefault()
    if (!q.trim()) return
    setSearching(true)
    setSearchErr(null)
    try {
      setResults(await geocode(q))
    } catch (err) {
      setSearchErr(err instanceof Error ? err.message : 'search failed')
    } finally {
      setSearching(false)
    }
  }

  const canFind = p.start != null && (p.loop || p.end != null) && !p.loading

  return (
    <aside className="sidebar">
      <h1>scenic-route</h1>

      <form onSubmit={search} className="search">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search address or place"
          aria-label="Search address"
        />
        <button type="submit" disabled={searching}>
          {searching ? '…' : 'Go'}
        </button>
      </form>
      {searchErr && <p className="err">{searchErr}</p>}
      {results.length > 0 && (
        <ul className="results">
          {results.map((r) => (
            <li key={`${r.lat},${r.lon}`}>
              <button
                onClick={() => {
                  p.onPickPlace({ lat: r.lat, lon: r.lon })
                  setResults([])
                  setQ('')
                }}
              >
                {r.label}
              </button>
            </li>
          ))}
        </ul>
      )}

      <fieldset>
        <legend>Points</legend>
        <label>
          <input
            type="radio"
            name="active"
            checked={p.active === 'start'}
            onChange={() => p.onSetActive('start')}
          />
          Start {p.start ? '✓' : '—'}
        </label>
        <label>
          <input
            type="radio"
            name="active"
            checked={p.active === 'end'}
            disabled={p.loop}
            onChange={() => p.onSetActive('end')}
          />
          End {p.loop ? '(loop)' : p.end ? '✓' : '—'}
        </label>
        <p className="hint">Click the map or pick a search result to place the active point.</p>
        <label className="loop">
          <input type="checkbox" checked={p.loop} onChange={(e) => p.onSetLoop(e.target.checked)} />
          Loop from start
        </label>
      </fieldset>

      <fieldset>
        <legend>Ride</legend>
        <label>
          Target distance: <strong>{p.targetKm} km</strong>
          <input
            type="number"
            min={1}
            step={1}
            value={p.targetKm}
            onChange={(e) => p.onSetTargetKm(Number(e.target.value))}
          />
        </label>
        <label>
          Infra ↔ Scenic: <strong>{Math.round(p.balance * 100)}% scenic</strong>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={p.balance}
            onChange={(e) => p.onSetBalance(Number(e.target.value))}
          />
        </label>
        <label>
          Distance tolerance: <strong>±{Math.round(p.tolerancePct * 100)}%</strong>
          <input
            type="range"
            min={0}
            max={0.9}
            step={0.05}
            value={p.tolerancePct}
            onChange={(e) => p.onSetTolerance(Number(e.target.value))}
          />
        </label>
        <label>
          Suggestions: <strong>{p.numSuggestions}</strong>
          <input
            type="range"
            min={1}
            max={5}
            step={1}
            value={p.numSuggestions}
            onChange={(e) => p.onSetSuggestions(Number(e.target.value))}
          />
        </label>
        <label>
          Avoid backtracking: <strong>{Math.round(p.avoidBacktracking * 100)}%</strong>
          <input
            type="range"
            min={0}
            max={1}
            step={0.1}
            value={p.avoidBacktracking}
            onChange={(e) => p.onSetAvoidBacktracking(Number(e.target.value))}
          />
          <span className="hint">Penalize riding a road out and back within one route.</span>
        </label>
      </fieldset>

      <button className="find" onClick={p.onFind} disabled={!canFind}>
        {p.loading ? 'Finding routes…' : 'Find routes'}
      </button>
      {p.error && <p className="err">{p.error}</p>}

      {p.routes.length > 0 && (
        <ol className="routes">
          {p.routes.map((r) => (
            <li
              key={r.rank}
              className={r.rank === p.selectedRank ? 'route selected' : 'route'}
              onMouseEnter={() => p.onSelectRoute(r.rank)}
              onMouseLeave={() => p.onSelectRoute(null)}
            >
              <span className="swatch" style={{ background: strokeFor(r.rank) }} />
              <span className="meta">
                <strong>{(r.distanceM / 1000).toFixed(1)} km</strong> · score{' '}
                {r.blendedScore.toFixed(3)}
                <br />
                cqi {r.meanCqi.toFixed(2)} · scenic {r.meanScenic.toFixed(2)}
              </span>
              <button
                className="gpx"
                onClick={() => p.onDownloadGpx(r)}
                title="Download GPX"
              >
                ⬇ GPX
              </button>
            </li>
          ))}
        </ol>
      )}
    </aside>
  )
}
