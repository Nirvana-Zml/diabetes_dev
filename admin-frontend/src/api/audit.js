import { get, del } from '@/utils/request'
import http from '@/utils/request'
import { toSnakeCase } from '@/utils/normalize'

function buildQueryParams(params = {}) {
  const query = {
    userId: params.userId,
    action: params.action,
    keyword: params.keyword,
    result: params.result,
    startTime: params.startTime,
    endTime: params.endTime,
  }
  if (params.actions) {
    query.actions = params.actions
  }
  if (params.page) query.page = params.page
  if (params.size) query.size = params.size
  return Object.fromEntries(Object.entries(query).filter(([, v]) => v !== undefined && v !== null && v !== ''))
}

function normalizeLog(item) {
  const log = toSnakeCase(item)
  log.log_id = item.logId || item.log_id || log.log_id
  log.user_id = item.userId || item.user_id || log.user_id
  log.ip_address = item.ipAddress || item.ip_address || log.ip_address
  log.user_agent = item.userAgent || item.user_agent || log.user_agent
  log.created_at = item.createdAt || item.created_at || log.created_at
  return log
}

export async function getAdminAuditLogs(params = {}) {
  const data = await get('/admin/audit/logs', {
    params: {
      ...buildQueryParams(params),
      page: params.page || 1,
      size: params.size || 20,
    },
  })
  const list = (data.logs || []).map(normalizeLog)
  return {
    list,
    total: data.total ?? list.length,
    page: data.page ?? params.page ?? 1,
    size: data.size ?? params.size ?? 20,
  }
}

export async function getAdminAuditLogDetail(logId) {
  const data = await get(`/admin/audit/logs/${logId}`)
  return normalizeLog(data)
}

export async function getAdminAuditActions() {
  const data = await get('/admin/audit/logs/actions')
  return data.actions || []
}

export function getAdminAuditOverview(days = 7) {
  return get('/admin/audit/logs/overview', { params: { days } })
}

export function deleteAdminAuditLog(logId) {
  return del(`/admin/audit/logs/${logId}`)
}

export function batchDeleteAdminAuditLogs(logIds) {
  return del('/admin/audit/logs', {
    data: { logIds },
  })
}

export async function exportAdminAuditLogs(params = {}) {
  const res = await http.request({
    method: 'GET',
    url: '/admin/audit/logs/export',
    params: buildQueryParams(params),
    responseType: 'blob',
  })
  const payload = res?.data
  const blob = payload instanceof Blob
    ? payload
    : new Blob([payload?.data || payload || ''], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `audit_logs_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
