import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  getV2: vi.fn(),
  toSnakeCase: vi.fn(),
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

vi.mock('@/utils/normalize', async (importActual) => {
  const actual = await importActual()
  return { ...actual, toSnakeCase: mocks.toSnakeCase }
})

beforeEach(async () => {
  vi.clearAllMocks()
  vi.resetModules()
  const actual = await vi.importActual('@/utils/normalize')
  mocks.toSnakeCase.mockImplementation(actual.toSnakeCase)
  mocks.get.mockResolvedValue({})
  mocks.getV2.mockResolvedValue({})
})

describe('final core branch coverage', () => {
  it('covers remaining api normalization branches', async () => {
    mocks.toSnakeCase.mockReturnValueOnce({})
    const consultation = await import('../consultation')
    expect(consultation.normalizeConsultMessageForTest({ messageId: 'only-id' })).toMatchObject({
      message_id: 'only-id',
    })
    expect(consultation.normalizeConsultMessageForTest({})).toMatchObject({
      message_id: '',
    })

    const actual = await vi.importActual('@/utils/normalize')
    mocks.toSnakeCase.mockImplementation(actual.toSnakeCase)
    mocks.getV2.mockResolvedValueOnce({ list: [{ sessionId: 'only-list' }] })
    await expect(consultation.listConsultations()).resolves.toMatchObject({
      list: [expect.objectContaining({ session_id: 'only-list' })],
    })

    const home = await import('../home')
    expect(home.normalizeBannerForTest({ bannerId: 'only-banner' })).toMatchObject({ id: 'only-banner' })
    expect(home.normalizeBannerForTest({ id: 'only-id-fallback' })).toMatchObject({ id: 'only-id-fallback' })
    mocks.getV2.mockResolvedValueOnce({
      list: [{ doctorId: 'doc-list', name: '医生', department: '内分泌科', status: 1 }],
    })
    await expect(home.getDoctors()).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'doc-list' }),
    ])

    const plan = await import('../plan')
    mocks.get.mockResolvedValueOnce({ list: [{ planId: 'only-plan-list' }] })
    await expect(plan.getPlanHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ plan_id: 'only-plan-list' })],
    })

    const risk = await import('../risk')
    mocks.get.mockResolvedValueOnce({ list: [{ assessmentId: 'only-risk-list', riskScore: 1 }] })
    await expect(risk.getRiskHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ assessment_id: 'only-risk-list' })],
    })

    const reminder = await import('../checkinReminder')
    mocks.get.mockResolvedValueOnce([{ checkinType: 1, remindTime: '08:00' }])
    await expect(reminder.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 1 }),
    ])
  })
})
