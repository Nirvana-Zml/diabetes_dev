import { describe, expect, it } from 'vitest'
import { AUDIT_ACTION_LABELS, getAuditActionLabel, LOGIN_ACTIONS } from '../auditActions'

describe('auditActions utils', () => {
  it('maps known audit action codes to labels', () => {
    expect(getAuditActionLabel('admin.login')).toBe('管理员登录')
    expect(getAuditActionLabel('article.publish')).toBe('资讯发布')
    expect(getAuditActionLabel('unknown.action')).toBe('unknown.action')
    expect(getAuditActionLabel()).toBe('—')
    expect(getAuditActionLabel('')).toBe('—')
  })

  it('exports login action list and label map', () => {
    expect(LOGIN_ACTIONS).toContain('admin.login')
    expect(AUDIT_ACTION_LABELS['audit.export']).toBe('审计日志导出')
  })
})
