import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  difyQaChat: vi.fn(async () => null),
  difyPlanGenerate: vi.fn(async () => null),
  get: vi.fn(async (url) => {
    if (url === '/checkin/achievements') {
      return {
        achievements: [{
          id: 'first_checkin',
          name: '首次打卡',
          unlocked: true,
          unlocked_at: '2026-06-01',
        }],
      }
    }
    if (url === '/checkin/stats') {
      return { totalCheckins: 5, completionRate: 0.5, streakDays: 2, totalPoints: 50 }
    }
    return {}
  }),
}))

vi.mock('@/config', () => ({
  USE_MOCK: true,
  DIFY_WORKFLOWS: {
    QA: 'qa-url',
    CONSULTATION: 'consult-url',
    RISK: 'risk-url',
    PLAN: 'plan-url',
    ARTICLE_DRAFT: 'article-url',
  },
}))

vi.mock('@/utils/delay', () => ({
  delay: vi.fn(async () => {}),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

vi.mock('@/utils/sse', () => ({
  fetchBackendSSE: vi.fn(),
  mockSSEStream: vi.fn(async (_chunks, onChunk, onEnd) => onEnd?.()),
}))

vi.mock('@/api/dify', async (importActual) => {
  const actual = await importActual()
  return {
    ...actual,
    difyQaChat: mocks.difyQaChat,
    difyPlanGenerate: mocks.difyPlanGenerate,
  }
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.resetModules()
})

describe('mock-mode api branches', () => {
  it('delegates chatQA to dify when mock mode is enabled', async () => {
    const chat = await import('../chat')
    await chat.chatQA('饮食建议', { conversationId: 'c-mock' })
    expect(mocks.difyQaChat).toHaveBeenCalledWith('饮食建议', { conversationId: 'c-mock' })
  })

  it('delegates generatePlan to dify when mock mode is enabled', async () => {
    const plan = await import('../plan')
    const onStage = vi.fn()
    await plan.generatePlan({ onStage })
    expect(mocks.difyPlanGenerate).toHaveBeenCalledWith({ onStage })
  })

  it('logs dify placeholder output in mock mode', async () => {
    const info = vi.spyOn(console, 'info').mockImplementation(() => {})
    const dify = await import('../dify')
    await dify.difyConsultationSuggest('血糖偏高')
    expect(info).toHaveBeenCalled()
    info.mockRestore()
  })

  it('builds achievement wall mock overrides when mock mode is enabled', async () => {
    const checkin = await import('../checkin')
    const wall = await checkin.getAchievementWall()
    expect(wall.achievements.some((item) => item.id === 'first_checkin')).toBe(true)
    expect(mocks.get).toHaveBeenCalled()
  })

  it('reads risk history records in mock mode', async () => {
    mocks.get.mockResolvedValueOnce({
      records: [{ assessmentId: 'r-mock', riskScore: 40 }],
      total: 2,
    })
    const risk = await import('../risk')
    await expect(risk.getRiskHistory()).resolves.toMatchObject({
      total: 2,
      list: [expect.objectContaining({ assessment_id: 'r-mock' })],
    })
  })
})
