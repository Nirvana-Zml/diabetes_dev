/** 审计操作类型 code → 中文展示名 */
export const AUDIT_ACTION_LABELS = {
  'user.login': '用户登录',
  'user.logout': '用户登出',
  'user.register': '用户注册',
  'user.password.change': '用户修改密码',
  'user.password.reset': '用户重置密码',
  'admin.login': '管理员登录',
  'admin.logout': '管理员登出',
  'admin.password.change': '管理员修改密码',
  'data.export': '数据导出',
  'article.create': '资讯创建',
  'article.update': '资讯编辑',
  'article.delete': '资讯删除',
  'article.cover.upload': '资讯封面上传',
  'article.review': '资讯审核',
  'article.publish': '资讯发布',
  'video.create': '视频创建',
  'video.update': '视频编辑',
  'video.delete': '视频删除',
  'video.cover.upload': '视频封面上传',
  'video.file.upload': '视频文件上传',
  'audit.delete': '审计日志删除',
  'audit.export': '审计日志导出',
}

export const LOGIN_ACTIONS = ['user.login', 'user.logout', 'admin.login', 'admin.logout']

export function getAuditActionLabel(action) {
  if (!action) return '—'
  return AUDIT_ACTION_LABELS[action] || action
}
