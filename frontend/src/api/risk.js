import { get, post } from '@/utils/request'
import {
  normalizeRiskResult,
  mapRiskRequest,
  toSnakeCase,
} from '@/utils/normalize'
import { difyRiskAssess } from '@/api/dify'
import { mockRiskResult } from '@/mock/data'

/** Dify 风险评估阻塞调用，需与后端 dify.timeout-seconds（默认 120s）对齐 */
const RISK_ASSESS_TIMEOUT_MS = 130000

/** POST /api/risk/assess — 后端 SpringBoot + Dify 工作流 */
export function assessRisk(data) {
  return post('/risk/assess', mapRiskRequest(data), {
    timeout: RISK_ASSESS_TIMEOUT_MS,
    mockFn: async () => {
      const result = await difyRiskAssess(data)
      return { assessment_id: 'ra_' + Date.now(), ...result }
    },
  }).then(normalizeRiskResult)
}

/** GET /api/risk/history */
function resolveRiskRecords(raw = {}) {
  return raw.records ?? raw.list ?? []
}

export async function getRiskHistory(params = {}) {
  const data = await get('/risk/history', {
    params: { page: params.page || 1, size: params.page_size || 10 },
    mockFn: async () => ({
      records: [mockRiskResult],
      total: 1,
    }),
  })
  const raw = data
  const records = resolveRiskRecords(raw)
  return {
    list: records.map((r) => toSnakeCase(r)),
    total: raw.total ?? records.length,
    page: params.page || 1,
    page_size: params.page_size || 10,
  }
}

/** GET /api/risk/{id} */
export function getRiskDetail(id) {
  return get(`/risk/${id}`, { mockFn: async () => mockRiskResult }).then(normalizeRiskResult)
}

/** @internal 供单元测试覆盖历史列表字段回退 */
export function resolveRiskRecordsForTest(raw) {
  return resolveRiskRecords(raw)
}
