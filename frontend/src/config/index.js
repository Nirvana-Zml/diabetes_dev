/** 是否使用 Mock 占位数据（不请求真实后端） */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

/** 后端 API 基础路径（现有服务） */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

/** 新服务 API 基础路径 */
export const API_V2_BASE = import.meta.env.VITE_API_V2_BASE || '/api/v2'

/** 管理后台地址（与用户端分离部署） */
export const ADMIN_PORTAL_URL = import.meta.env.VITE_ADMIN_PORTAL_URL || 'http://localhost:5174'

/**
 * Dify 工作流 URL 占位（对接时替换为真实地址）
 * @see 系统详细设计说明书 4.1 Dify 智能体工作流
 */
export const DIFY_WORKFLOWS = {
  /** 科普问答工作流 */
  QA: import.meta.env.VITE_DIFY_QA_URL || '__PLACEHOLDER_DIFY_QA_WORKFLOW__',
  /** 问诊辅助工作流 */
  CONSULTATION: import.meta.env.VITE_DIFY_CONSULTATION_URL || '__PLACEHOLDER_DIFY_CONSULTATION_WORKFLOW__',
  /** 风险评估工作流 */
  RISK: import.meta.env.VITE_DIFY_RISK_URL || '__PLACEHOLDER_DIFY_RISK_WORKFLOW__',
  /** 方案生成工作流 */
  PLAN: import.meta.env.VITE_DIFY_PLAN_URL || '__PLACEHOLDER_DIFY_PLAN_WORKFLOW__',
  /** 资讯 AI 初稿工作流 */
  ARTICLE_DRAFT: import.meta.env.VITE_DIFY_ARTICLE_DRAFT_URL || '__PLACEHOLDER_DIFY_ARTICLE_DRAFT_WORKFLOW__',
}

export const DISCLAIMER =
  '⚠️ 以上内容由AI生成，仅供参考，不能替代专业医生的诊断和治疗建议。如有健康问题，请及时前往正规医疗机构就医。'
