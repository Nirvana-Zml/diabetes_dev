import { get, post } from '@/utils/request'
import { USE_MOCK } from '@/config'
import { difyPlanGenerate } from '@/api/dify'
import { fetchBackendSSE } from '@/utils/sse'
import { normalizePlan, toSnakeCase } from '@/utils/normalize'
import { mockHealthPlan } from '@/mock/data'

const STAGE_MAP = {
  stage_calorie: 'calorie',
  stage_diet: 'diet',
  stage_exercise: 'exercise',
  stage_rest: 'rest',
  stage_medication: 'medication',
  complete: 'complete',
  error: 'error',
}

/**
 * Dify 方案生成工作流 JSON 契约（后端 plan-service 组装后传入 inputs Object）：
 * 详见 GET /api/v1/plan/dify-workflow-spec
 *
 * 入参平铺写入 Dify API inputs（7 个开始节点变量）:
 * query, user_id, daily_calories, user_profile, health_profile, risk_data?, checkin_data
 *
 * 出参 outputs.plan_llm_output（浅层 LLM Structured Output，后端组装为完整方案）
 */

/** POST /api/plan/generate — SSE 分阶段返回 */
export async function generatePlan(options = {}) {
  if (USE_MOCK) {
    return difyPlanGenerate(options)
  }
  const { onStage } = options
  await fetchBackendSSE('/plan/generate', ({ event, data }) => {
    const stage = STAGE_MAP[event] || data.stage
    const payload = toSnakeCase(data)
    if (stage === 'error') {
      throw new Error(payload.message || '方案生成失败')
    }
    if (stage === 'calorie') {
      onStage?.({ stage: 'calorie', daily_calories: payload.daily_calories ?? payload.dailyCalories })
    } else if (stage === 'complete') {
      onStage?.({ stage: 'complete', plan_id: payload.plan_id ?? payload.planId })
    } else if (stage === 'medication') {
      onStage?.({ stage: 'medication', content: payload.content })
    } else if (stage) {
      onStage?.({ stage, content: payload.content || payload })
    }
  })
}

/** GET /api/plan/latest */
export function getLatestPlan() {
  return get('/plan/latest', { mockFn: async () => mockHealthPlan }).then(normalizePlan)
}

/** GET /api/plan/{id} */
export function getPlanDetail(id) {
  return get(`/plan/${id}`, { mockFn: async () => mockHealthPlan }).then(normalizePlan)
}

/** GET /api/plan/history */
export async function getPlanHistory(params = {}) {
  const data = await get('/plan/history', {
    params: { page: params.page || 1, size: params.size || 10 },
    mockFn: async () => ({
      plans: [
        mockHealthPlan,
        { ...mockHealthPlan, plan_id: 'plan_000', version: 1, generated_at: '2026-06-10T08:00:00+08:00' },
      ],
      total: 2,
    }),
  })
  const plans = (data.plans || data.list || []).map((p) => toSnakeCase(p))
  return { list: plans, total: data.total ?? plans.length }
}

/** POST /api/plan/{id}/favorite */
export function togglePlanFavorite(id) {
  return post(`/plan/${id}/favorite`, {}, { mockFn: async () => ({ favorited: true }) })
}
