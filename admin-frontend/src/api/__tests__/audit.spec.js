import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  del: vi.fn(),
  request: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  del: mocks.del,
  default: {
    request: mocks.request,
  },
}))

import {
  batchDeleteAdminAuditLogs,
  deleteAdminAuditLog,
  exportAdminAuditLogs,
  getAdminAuditActions,
  getAdminAuditLogDetail,
  getAdminAuditLogs,
  getAdminAuditOverview,
} from '../audit'

describe('audit api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('normalizes audit log list responses', async () => {
    mocks.get.mockResolvedValueOnce({
      logs: [{
        logId: 'l1',
        userId: 'u1',
        action: 'admin.login',
        ipAddress: '127.0.0.1',
        userAgent: 'vitest',
        createdAt: '2026-07-01T10:00:00+08:00',
      }],
      total: 1,
      page: 2,
      size: 10,
    })

    await expect(getAdminAuditLogs({
      userId: 'u1',
      action: 'admin.login',
      keyword: 'login',
      result: 'success',
      startTime: '2026-07-01',
      endTime: '2026-07-02',
      actions: ['admin.login'],
      page: 2,
      size: 10,
    })).resolves.toMatchObject({
      list: [expect.objectContaining({
        log_id: 'l1',
        user_id: 'u1',
        ip_address: '127.0.0.1',
        user_agent: 'vitest',
        created_at: '2026-07-01T10:00:00+08:00',
      })],
      total: 1,
      page: 2,
      size: 10,
    })
  })

  it('loads detail, actions and overview', async () => {
    mocks.get.mockResolvedValueOnce({ logId: 'l2', userId: 'u2' })
    await expect(getAdminAuditLogDetail('l2')).resolves.toMatchObject({ log_id: 'l2', user_id: 'u2' })

    mocks.get.mockResolvedValueOnce({ actions: ['admin.login'] })
    await expect(getAdminAuditActions()).resolves.toEqual(['admin.login'])

    mocks.get.mockResolvedValueOnce({ total: 10 })
    await expect(getAdminAuditOverview(14)).resolves.toEqual({ total: 10 })
    expect(mocks.get).toHaveBeenLastCalledWith('/admin/audit/logs/overview', { params: { days: 14 } })
  })

  it('deletes single and batch audit logs', async () => {
    mocks.del.mockResolvedValueOnce({ success: true })
    await expect(deleteAdminAuditLog('l1')).resolves.toEqual({ success: true })

    mocks.del.mockResolvedValueOnce({ deleted: 2 })
    await expect(batchDeleteAdminAuditLogs(['l1', 'l2'])).resolves.toEqual({ deleted: 2 })
    expect(mocks.del).toHaveBeenLastCalledWith('/admin/audit/logs', {
      data: { logIds: ['l1', 'l2'] },
    })
  })

  it('exports audit logs as downloadable blob', async () => {
    const click = vi.fn()
    const appendChild = vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    const removeChild = vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(document, 'createElement').mockReturnValue({
      href: '',
      download: '',
      click,
    })

    mocks.request.mockResolvedValueOnce({ data: new Blob(['csv'], { type: 'text/csv' }) })
    await exportAdminAuditLogs({ keyword: 'login' })
    expect(click).toHaveBeenCalled()
    expect(appendChild).toHaveBeenCalled()
    expect(removeChild).toHaveBeenCalled()

    mocks.request.mockResolvedValueOnce({ data: { data: 'csv-text' } })
    await exportAdminAuditLogs({})
    expect(click).toHaveBeenCalledTimes(2)

    mocks.request.mockResolvedValueOnce({ data: 'plain-csv' })
    await exportAdminAuditLogs({})
    expect(click).toHaveBeenCalledTimes(3)

    mocks.request.mockResolvedValueOnce({ data: null })
    await exportAdminAuditLogs({})
    expect(click).toHaveBeenCalledTimes(4)

    appendChild.mockRestore()
    removeChild.mockRestore()
  })

  it('handles empty list fallbacks', async () => {
    mocks.get.mockResolvedValueOnce({ logs: undefined, total: undefined })
    await expect(getAdminAuditLogs()).resolves.toMatchObject({
      list: [],
      total: 0,
      page: 1,
      size: 20,
    })

    mocks.get.mockResolvedValueOnce({
      logs: [{ logId: null, log_id: null, action: 'admin.login' }],
      total: 1,
    })
    await expect(getAdminAuditLogs()).resolves.toMatchObject({
      list: [expect.objectContaining({ action: 'admin.login' })],
    })

    mocks.get.mockResolvedValueOnce({ actions: undefined })
    await expect(getAdminAuditActions()).resolves.toEqual([])
  })
})
