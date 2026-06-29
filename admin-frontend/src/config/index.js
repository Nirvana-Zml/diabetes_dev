/** 是否使用 Mock 占位数据 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

/** 后端 API 基础路径（现有服务） */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

/** 新服务 API 基础路径 */
export const API_V2_BASE = import.meta.env.VITE_API_V2_BASE || '/api/v2'

/** 用户端门户地址 */
export const USER_PORTAL_URL = import.meta.env.VITE_USER_PORTAL_URL || 'http://localhost:5173'

/** Dify 资讯 AI 初稿工作流 */
export const DIFY_ARTICLE_DRAFT_URL =
  import.meta.env.VITE_DIFY_ARTICLE_DRAFT_URL || '__PLACEHOLDER_DIFY_ARTICLE_DRAFT_WORKFLOW__'
