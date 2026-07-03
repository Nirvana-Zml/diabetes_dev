import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearHealthTrendCache,
  readHealthTrendCache,
  writeHealthTrendCache,
} from '../useHealthTrendCache'

describe('useHealthTrendCache', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('reads and writes cached trend summary', () => {
    expect(readHealthTrendCache('u1')).toBeNull()
    expect(readHealthTrendCache()).toBeNull()

    writeHealthTrendCache('u1', { summary: '稳定', source: 'dify' }, '2026-06-30')
    expect(readHealthTrendCache('u1')).toMatchObject({
      summary: '稳定',
      source: 'dify',
      healthRecordedAt: '2026-06-30',
    })

    writeHealthTrendCache('u1', { summary: '' })
    expect(readHealthTrendCache('u1')?.summary).toBe('稳定')

    clearHealthTrendCache('u1')
    expect(readHealthTrendCache('u1')).toBeNull()
    clearHealthTrendCache()
  })

  it('ignores invalid cache payloads', () => {
    localStorage.setItem('health_trend_summary:u2', '{bad json')
    expect(readHealthTrendCache('u2')).toBeNull()
  })

  it('ignores storage quota errors when writing or clearing', () => {
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
      throw new Error('quota')
    })
    writeHealthTrendCache('u3', { summary: '趋势', source: 'dify' })
    expect(readHealthTrendCache('u3')).toBeNull()
    vi.mocked(localStorage.setItem).mockRestore()

    writeHealthTrendCache('u4', { summary: '可清除' })
    vi.spyOn(localStorage, 'removeItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    clearHealthTrendCache('u4')
    expect(readHealthTrendCache('u4')).toMatchObject({ summary: '可清除' })
    vi.mocked(localStorage.removeItem).mockRestore()
    clearHealthTrendCache('u4')
    expect(readHealthTrendCache('u4')).toBeNull()
  })
})
