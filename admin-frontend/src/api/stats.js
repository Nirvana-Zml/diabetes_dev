import { get } from '@/utils/request'

export function getStatsOverview() {
  return get('/admin/stats/overview')
}

export function getStatsTrends(days = 30) {
  return get('/admin/stats/trends', { params: { days } })
}

export function getStatsUsers(params = {}) {
  return get('/admin/stats/users', { params })
}

export function getStatsUserBrief(subjectId) {
  return get(`/admin/stats/users/${subjectId}/brief`)
}
