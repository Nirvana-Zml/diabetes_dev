import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  getV2: vi.fn(),
}))

vi.mock('@/config', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, USE_MOCK: false }
})

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  getV2: mocks.getV2,
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  postV2: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
})

describe('remaining api branch gaps', () => {
  it('covers list fallback resolver helpers', async () => {
    const consultation = await import('../consultation')
    const home = await import('../home')
    const plan = await import('../plan')
    const risk = await import('../risk')

    expect(consultation.resolveConsultSessionListForTest({ sessions: [{ sessionId: '1' }] })).toHaveLength(1)
    expect(consultation.resolveConsultSessionListForTest({ list: [{ sessionId: '2' }] })).toHaveLength(1)
    expect(consultation.resolveConsultSessionListForTest({})).toEqual([])

    expect(home.resolveBannerIdForTest({ banner_id: 'a' })).toBe('a')
    expect(home.resolveBannerIdForTest({ bannerId: 'b' })).toBe('b')
    expect(home.resolveBannerIdForTest({ id: 'c' })).toBe('c')
    expect(home.resolveBannerIdForTest({})).toBe('')

    expect(home.resolveDoctorRawListForTest([{ doctorId: 'd1' }])).toHaveLength(1)
    expect(home.resolveDoctorRawListForTest({ doctors: [{ doctorId: 'd2' }] })).toHaveLength(1)
    expect(home.resolveDoctorRawListForTest({ list: [{ doctorId: 'd3' }] })).toHaveLength(1)
    expect(home.resolveDoctorRawListForTest({})).toEqual([])

    expect(plan.resolvePlanHistoryListForTest({ plans: [{ planId: 'p1' }] })).toHaveLength(1)
    expect(plan.resolvePlanHistoryListForTest({ list: [{ planId: 'p2' }] })).toHaveLength(1)
    expect(plan.resolvePlanHistoryListForTest({})).toEqual([])

    expect(risk.resolveRiskRecordsForTest({ records: [{ assessmentId: 'r1' }] })).toHaveLength(1)
    expect(risk.resolveRiskRecordsForTest({ list: [{ assessmentId: 'r2' }] })).toHaveLength(1)
    expect(risk.resolveRiskRecordsForTest({})).toEqual([])
  })

  it('listConsultations falls back to list when sessions is absent', async () => {
    mocks.getV2.mockResolvedValue({ list: [{ sessionId: 'gap-list-only' }] })
    const { listConsultations } = await import('../consultation')
    await expect(listConsultations()).resolves.toMatchObject({
      list: [expect.objectContaining({ session_id: 'gap-list-only' })],
    })
  })

  it('getDoctors falls back to list when doctors field is absent', async () => {
    mocks.getV2.mockResolvedValue({
      list: [{ doctorId: 'gap-doc-list', name: '医生', department: '内分泌科', status: 1 }],
    })
    const { getDoctors } = await import('../home')
    await expect(getDoctors()).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'gap-doc-list' }),
    ])
  })

  it('getHomeContent normalizes banner id from item.id only', async () => {
    mocks.getV2.mockResolvedValue({
      banners: [{ id: 'gap-banner-id-only', title: 'Banner' }],
      videos: [],
    })
    const { getHomeContent } = await import('../home')
    await expect(getHomeContent()).resolves.toMatchObject({
      banners: [expect.objectContaining({ id: 'gap-banner-id-only' })],
    })
  })

  it('getPlanHistory falls back to list when plans is absent', async () => {
    mocks.get.mockResolvedValue({ list: [{ planId: 'gap-plan-list-only' }] })
    const { getPlanHistory } = await import('../plan')
    await expect(getPlanHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ plan_id: 'gap-plan-list-only' })],
      total: 1,
    })
  })

  it('getRiskHistory falls back to list when records is absent', async () => {
    mocks.get.mockResolvedValue({ list: [{ assessmentId: 'gap-risk-list', riskScore: 12 }] })
    const { getRiskHistory } = await import('../risk')
    await expect(getRiskHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ assessment_id: 'gap-risk-list' })],
      total: 1,
    })
  })
})
