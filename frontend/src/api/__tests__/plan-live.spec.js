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

  it('maps stages from payload and falls back to raw payload content', async () => {
    const { generatePlan } = await import('../plan')
    const stages = []

    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'unknown', data: { stage: 'rest', content: '休息建议' } })
      onEvent({ event: 'unknown', data: { stage: 'custom', dailyCalories: 999 } })
    })

    await generatePlan({ onStage: (stage) => stages.push(stage) })

    expect(stages).toEqual([
      { stage: 'rest', content: '休息建议' },
      { stage: 'custom', content: { stage: 'custom', daily_calories: 999 } },
    ])
  })

  it('throws default plan error message when backend omits details', async () => {
    const { generatePlan } = await import('../plan')
    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'error', data: {} })
    })

    await expect(generatePlan()).rejects.toThrow('方案生成失败')
  })

  it('maps calorie stage from snake_case payload fields', async () => {
    const { generatePlan } = await import('../plan')
    const stages = []
    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'stage_calorie', data: { daily_calories: 1500 } })
    })
    await generatePlan({ onStage: (stage) => stages.push(stage) })
    expect(stages).toEqual([{ stage: 'calorie', daily_calories: 1500 }])
  })

  it('maps complete stage from snake_case plan_id and calorie from camelCase', async () => {
    const { generatePlan } = await import('../plan')
    const stages = []
    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'stage_calorie', data: { dailyCalories: 1400 } })
      onEvent({ event: 'complete', data: { plan_id: 'p-snake' } })
    })
    await generatePlan({ onStage: (stage) => stages.push(stage) })
    expect(stages).toEqual([
      { stage: 'calorie', daily_calories: 1400 },
      { stage: 'complete', plan_id: 'p-snake' },
    ])
  })

  it('maps camelCase-only calorie and complete payload fields', async () => {
    const { generatePlan } = await import('../plan')
    const stages = []
    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'stage_calorie', data: { dailyCalories: 1314 } })
      onEvent({ event: 'complete', data: { planId: 'plan-camel-only' } })
    })
    await generatePlan({ onStage: (stage) => stages.push(stage) })
    expect(stages).toEqual([
      { stage: 'calorie', daily_calories: 1314 },
      { stage: 'complete', plan_id: 'plan-camel-only' },
    ])
  })

  it('tolerates missing onStage callback and reads plan history list field', async () => {
    const { generatePlan, getPlanHistory } = await import('../plan')
    const request = await import('@/utils/request')

    mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
      onEvent({ event: 'stage_calorie', data: { daily_calories: 1200, dailyCalories: 999 } })
      onEvent({ event: 'complete', data: { planId: 'p-camel' } })
    })
    await expect(generatePlan()).resolves.toBeUndefined()

    request.get.mockResolvedValueOnce({
      plans: [{ planId: 'p-from-plans' }],
    })
    await expect(getPlanHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ plan_id: 'p-from-plans' })],
      total: 1,
    })

    request.get.mockResolvedValueOnce({
      list: [{ planId: 'p-list-only' }],
    })
    await expect(getPlanHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ plan_id: 'p-list-only' })],
      total: 1,
    })
  })

  it('uses dailyCalories and planId fallbacks when snake_case keys are absent', async () => {
    const normalize = await import('@/utils/normalize')
    const spy = vi.spyOn(normalize, 'toSnakeCase').mockImplementation((data) => data)
    try {
      const { generatePlan } = await import('../plan')
      const stages = []
      mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
        onEvent({ event: 'stage_calorie', data: { dailyCalories: 1777 } })
        onEvent({ event: 'complete', data: { planId: 'plan-fallback-id' } })
      })
      await generatePlan({ onStage: (stage) => stages.push(stage) })
      expect(stages).toEqual([
        { stage: 'calorie', daily_calories: 1777 },
        { stage: 'complete', plan_id: 'plan-fallback-id' },
      ])
    } finally {
      spy.mockRestore()
    }
  })

  it('reads plan history from list-only payload', async () => {
    const request = await import('@/utils/request')
    request.get.mockResolvedValueOnce({
      list: [{ planId: 'p-list-only-branch' }],
    })
    const { getPlanHistory } = await import('../plan')
    await expect(getPlanHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ plan_id: 'p-list-only-branch' })],
      total: 1,
    })
  })
})
