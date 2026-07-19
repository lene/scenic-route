import { describe, it, expect, vi } from 'vitest'
import { gpxBlob, downloadGpx } from './gpx'

describe('gpxBlob', () => {
  it('wraps text as a gpx blob', () => {
    const b = gpxBlob('<gpx/>')
    expect(b.type).toBe('application/gpx+xml')
    expect(b.size).toBe('<gpx/>'.length)
  })
})

describe('downloadGpx', () => {
  it('creates an object URL and clicks a download anchor', () => {
    const createObjectURL = vi.fn(() => 'blob:x')
    const revokeObjectURL = vi.fn()
    const prevCreate = (URL as unknown as { createObjectURL?: unknown }).createObjectURL
    const prevRevoke = (URL as unknown as { revokeObjectURL?: unknown }).revokeObjectURL
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    downloadGpx('route-1.gpx', '<gpx/>')

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:x')

    click.mockRestore()
    Object.assign(URL, { createObjectURL: prevCreate, revokeObjectURL: prevRevoke })
  })
})
