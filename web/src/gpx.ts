// Per-route GPX download: the backend returns each ride's GPX as a string in the
// /routes response, so export is a client-side blob save — no round trip.

export function gpxBlob(gpx: string): Blob {
  return new Blob([gpx], { type: 'application/gpx+xml' })
}

export function downloadGpx(filename: string, gpx: string): void {
  const url = URL.createObjectURL(gpxBlob(gpx))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
