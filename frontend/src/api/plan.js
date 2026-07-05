import { get, post } from '@/utils/request'
import * as appConfig from '@/config'
import { difyPlanGenerate } from '@/api/dify'
import { fetchBackendSSE } from '@/utils/sse'
import { normalizePlan, toSnakeCase } from '@/utils/normalize'
import { mockHealthPlan } from '@/mock/data'

function parseFavoriteToggle(res) {
  if (res == null || typeof res === 'string') return null
  if (typeof res !== 'object') return null

  if (res.data != null && typeof res.data === 'object' && !Array.isArray(res.data)) {
    const inner = parseFavoriteToggle(res.data)
    if (inner !== null) return inner
  }

  if ('favorited' in res) {
    return res.favorited === true || res.favorited === 1 || res.favorited === '1'
  }

  const normalized = toSnakeCase(res)
  const flag = normalized.is_favorite
  if (flag === false || flag === 0 || flag === '0') return false
  if (flag === true || flag === 1 || flag === '1') return true
  return null
}

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
  if (appConfig.USE_MOCK) {
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
      const dailyCalories = payload.daily_calories ?? payload.dailyCalories
      if (onStage) onStage({ stage: 'calorie', daily_calories: dailyCalories })
    } else if (stage === 'complete') {
      const planId = payload.plan_id ?? payload.planId
      if (onStage) onStage({ stage: 'complete', plan_id: planId })
    } else if (stage === 'medication') {
      if (onStage) onStage({ stage: 'medication', content: payload.content })
    } else if (stage) {
      if (onStage) onStage({ stage, content: payload.content || payload })
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
function resolvePlanHistoryList(data = {}) {
  return data.plans ?? data.list ?? []
}

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
  const plans = resolvePlanHistoryList(data).map((p) => toSnakeCase(p))
  return { list: plans, total: data.total ?? plans.length }
}

/** POST /api/plan/{id}/favorite — 切换收藏状态 */
const mockFavoritePlanIds = new Set()

export function togglePlanFavorite(id, currentlyFavorited = false) {
  return post(`/plan/${id}/favorite`, {}, {
    mockFn: async () => {
      if (mockFavoritePlanIds.has(id)) {
        mockFavoritePlanIds.delete(id)
        return { favorited: false }
      }
      mockFavoritePlanIds.add(id)
      return { favorited: true }
    },
  }).then((res) => {
    let favorited = parseFavoriteToggle(res)
    if (favorited === null) {
      // 旧版接口仅返回「收藏成功」等文案，按当前 UI 状态取反
      favorited = !currentlyFavorited
    }
    return { favorited }
  })
}

/** @internal 供单元测试覆盖历史列表字段回退 */
export function resolvePlanHistoryListForTest(data) {
  return resolvePlanHistoryList(data)
}
