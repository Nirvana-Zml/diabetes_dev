import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchBackendSSE: vi.fn(),
}))

vi.mock('@/config', () => ({
  USE_MOCK: false,
}))

vi.mock('@/api/dify', () => ({
  difyPlanGenerate: vi.fn(),
}))

vi.mock('@/utils/sse', () => ({
  fetchBackendSSE: mocks.fetchBackendSSE,
}))

vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
})

describe('plan api backend SSE mode', () => {
  it('maps backend plan stages', async () => {
    const { generatePlan } = await import('../plan')
    const stages = []

    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'stage_calorie', data: { dailyCalories: 1600 } })
      onEvent({ event: 'stage_diet', data: { content: '饮食' } })
      onEvent({ event: 'stage_exercise', data: { content: '运动' } })
      onEvent({ event: 'stage_rest', data: { content: '休息' } })
      onEvent({ event: 'stage_medication', data: { content: '用药' } })
      onEvent({ event: 'complete', data: { planId: 'p1' } })
    })

    await generatePlan({ onStage: (stage) => stages.push(stage) })

    expect(stages).toEqual([
      { stage: 'calorie', daily_calories: 1600 },
      { stage: 'diet', content: '饮食' },
      { stage: 'exercise', content: '运动' },
      { stage: 'rest', content: '休息' },
      { stage: 'medication', content: '用药' },
      { stage: 'complete', plan_id: 'p1' },
    ])
  })

  it('throws backend plan error stages', async () => {
    const { generatePlan } = await import('../plan')
    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'error', data: { message: '生成失败' } })
    })

    await expect(generatePlan()).rejects.toThrow('生成失败')
  })
})
