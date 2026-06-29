import { get, post } from '@/utils/request'
import { buildDateRange, normalizeCheckinStats, toSnakeCase } from '@/utils/normalize'
import { mockAiSummary } from '@/mock/data'

function withDateParams(params = {}) {
  const { startDate, endDate } = buildDateRange(params)
  return { startDate, endDate }
}

/** GET /api/checkin-management/stats */
export function getManagementStats(params = {}) {
  return get('/checkin-management/stats', {
    params: withDateParams(params),
    mockFn: async () => ({
      total_checkins: 45,
      completion_rate: 0.82,
      streak_days: 12,
      total_points: 520,
    }),
  }).then(normalizeCheckinStats)
}

/** GET /api/checkin-management/trends */
export async function getManagementTrends(params = {}) {
  const data = await get('/checkin-management/trends', {
    params: withDateParams(params),
    mockFn: async () => {
      const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
      const gen = () => days.map((d, i) => ({ date: d, count: Math.floor(Math.random() * 3) + (i % 2) }))
      return { diet_trend: gen(), exercise_trend: gen(), medication_trend: gen(), glucose_trend: gen() }
    },
  })
  const t = toSnakeCase(data)
  return {
    diet: t.diet_trend || [],
    exercise: t.exercise_trend || [],
    medication: t.medication_trend || [],
    glucose: t.glucose_trend || [],
  }
}

/** GET /api/checkin-management/ai-summary — 后端调用 Dify，前端解析结构化结果 */
export async function getAiSummary(params = {}) {
  const data = await get('/checkin-management/ai-summary', {
    params: withDateParams(params),
    mockFn: async () => ({
      summary: mockAiSummary,
      behavior_patterns: [
        { type: 'diet', pattern: '规律', completion_rate: 0.85, description: '饮食打卡较稳定', suggestion: '继续保持' },
        { type: 'exercise', pattern: '不稳定', completion_rate: 0.6, description: '运动打卡集中在周末', suggestion: '工作日安排30分钟快走' },
      ],
      anomalies: [
        { date: '2026-06-05', type: 'missed_all', description: '当日全部打卡缺失', possible_reason: '可能外出' },
      ],
      improvements: ['建议固定每日打卡时间', '增加工作日运动安排', '加强餐后血糖监测'],
    }),
  })
  const s = toSnakeCase(data)
  const patterns = (s.behavior_patterns || []).map((p) => ({
    type: p.type,
    pattern: p.pattern,
    completion_rate: p.completion_rate ?? p.completionRate ?? null,
    description: p.description || '',
    suggestion: p.suggestion || '',
  }))
  const anomalyList = (s.anomalies || []).map((a) => ({
    date: a.date || '',
    type: a.type || '',
    value: a.value ?? null,
    description: a.description || '',
    possible_reason: a.possible_reason || a.possibleReason || '',
  }))
  return {
    ai_summary: s.summary || s.ai_summary || mockAiSummary,
    behavior_patterns: patterns,
    anomalies: anomalyList,
    improvements: s.improvements || [],
    source: s.source || 'dify',
  }
}

/** POST /api/checkin-management/export */
export function exportReport(data) {
  const range = withDateParams(data)
  return post('/checkin-management/export', {
    startDate: range.startDate,
    endDate: range.endDate,
    format: data.format || 'pdf',
  }, {
    mockFn: async () => ({ task_id: 'task_mock', status: 'processing' }),
  }).then((res) => ({
    export_url: res.download_url || res.task_id || '__PLACEHOLDER_EXPORT_URL__',
    ...toSnakeCase(res),
  }))
}
