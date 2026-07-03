import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  get: vi.fn(),
}))

import { get } from '@/utils/request'
import {
  getStatsOverview,
  getStatsTrends,
  getStatsUserBrief,
  getStatsUsers,
} from '../stats'

describe('stats api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads overview and trends', async () => {
    get.mockResolvedValueOnce({ totalUsers: 100 })
    await expect(getStatsOverview()).resolves.toEqual({ totalUsers: 100 })
    expect(get).toHaveBeenCalledWith('/admin/stats/overview')

    get.mockResolvedValueOnce({ points: [] })
    await expect(getStatsTrends(7)).resolves.toEqual({ points: [] })
    expect(get).toHaveBeenLastCalledWith('/admin/stats/trends', { params: { days: 7 } })
  })

  it('loads user stats list and brief profile', async () => {
    get.mockResolvedValueOnce({ users: [], total: 0 })
    await expect(getStatsUsers({ page: 2, size: 10 })).resolves.toEqual({ users: [], total: 0 })
    expect(get).toHaveBeenLastCalledWith('/admin/stats/users', { params: { page: 2, size: 10 } })

    get.mockResolvedValueOnce({ subjectId: 'u1', nickname: '张三' })
    await expect(getStatsUserBrief('u1')).resolves.toEqual({ subjectId: 'u1', nickname: '张三' })
    expect(get).toHaveBeenLastCalledWith('/admin/stats/users/u1/brief')
  })
})
