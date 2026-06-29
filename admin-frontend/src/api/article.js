import { get, post, put, del } from '@/utils/request'
import { USE_MOCK } from '@/config'
import { normalizeArticle, toCamelCase, toSnakeCase } from '@/utils/normalize'
import { mockArticles, mockArticleContent } from '@/mock/data'
import { streamArticleDraft } from '@/utils/difyArticleDraft'

export const categoryMap = {
  diabetes_basics: '糖尿病基础',
  diet: '饮食管理',
  exercise: '运动康复',
  medication: '用药指导',
  complications: '并发症',
}

const statusMap = { draft: 1, pending: 2, published: 3, rejected: 4 }

function normalizeAdminArticle(item) {
  const a = normalizeArticle(item)
  if (item.status && typeof item.status === 'string') {
    a.status = item.status
  } else if (typeof item.status === 'number') {
    a.status = Object.keys(statusMap).find((k) => statusMap[k] === item.status) || 'draft'
  }
  a.reject_reason = item.rejectReason || item.reject_reason
  a.created_at = item.createdAt || item.created_at
  a.updated_at = item.updatedAt || item.updated_at
  return a
}

export async function getAdminArticles(params = {}) {
  const statusKey = params.status
  const status = statusKey ? statusMap[statusKey] : undefined
  const data = await get('/admin/articles', {
    params: {
      status,
      keyword: params.keyword,
      page: params.page || 1,
      size: params.size || 20,
    },
    mockFn: async () => ({
      articles: mockArticles.map((a, i) => ({
        ...a,
        status: i === 0 ? 'published' : 'draft',
      })),
      total: mockArticles.length,
    }),
  })
  const list = (data.articles || []).map(normalizeAdminArticle)
  return { list, total: data.total ?? list.length }
}

export async function getAdminArticleDetail(id) {
  const data = await get(`/admin/articles/${id}`, {
    mockFn: async () => {
      const article = mockArticles.find((a) => a.article_id === id) || mockArticles[0]
      return { ...article, content: mockArticleContent, status: 'draft' }
    },
  })
  return normalizeAdminArticle(data)
}

export function createAdminArticle(payload) {
  return post('/admin/articles', toCamelCase(payload), {
    mockFn: USE_MOCK ? async () => ({ articleId: 'art_new_' + Date.now(), status: 'draft' }) : undefined,
  }).then(toSnakeCase)
}

export function updateAdminArticle(id, payload) {
  return put(`/admin/articles/${id}`, toCamelCase(payload), {
    mockFn: USE_MOCK ? async () => ({ articleId: id, status: 'draft' }) : undefined,
  }).then(toSnakeCase)
}

/** POST /api/admin/articles/{id}/cover — 上传封面至 MinIO article/{id}.jpg（始终走真实后端） */
export function uploadAdminArticleCover(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return post(`/admin/articles/${id}/cover`, formData).then(toSnakeCase)
}

export function deleteAdminArticle(id) {
  return del(`/admin/articles/${id}`, {
    mockFn: async () => '删除成功',
  })
}

export function submitAdminArticle(id) {
  return put(`/admin/articles/${id}/submit`, {}, {
    mockFn: async () => ({ articleId: id, status: 'pending' }),
  }).then(toSnakeCase)
}

export function reviewAdminArticle(id, action, reason) {
  return put(`/admin/articles/${id}/review`, { action, reason }, {
    mockFn: async () => ({ articleId: id, status: action === 'approve' ? 'published' : 'rejected' }),
  }).then(toSnakeCase)
}

export async function getPendingReviewArticles(params = {}) {
  const data = await get('/admin/articles/pending-review', {
    params: { page: params.page || 1, size: params.size || 20 },
    mockFn: async () => ({ articles: mockArticles.slice(0, 1), total: 1 }),
  })
  const list = (data.articles || []).map(normalizeAdminArticle)
  return { list, total: data.total ?? list.length }
}

export function getAiDraftConfig() {
  return get('/admin/articles/ai-draft/config', {
    mockFn: async () => ({
      workflowUrl: '/dify-proxy/v1/workflows/run',
      apiKey: 'app-mock',
      responseMode: 'streaming',
      inputs: { topic: 'string(主题)', keywords: 'string(关键词)' },
    }),
  })
}

/**
 * 流式生成资讯 AI 初稿（管理端直调 Dify）。
 * @param {{ topic: string, keywords?: string }} input
 * @param {{ onChunk?: Function, onComplete?: Function }} callbacks
 */
export async function generateArticleDraft(input, callbacks = {}) {
  if (USE_MOCK) {
    await new Promise((r) => setTimeout(r, 600))
    const draft = {
      title: `${input.topic}：糖尿病患者必读指南`,
      summary: `关于${input.topic}的科普摘要，帮助糖友科学管理健康。`,
      content: `## ${input.topic}\n\n本文介绍${input.topic}相关的基本知识与实践建议。`,
      tags: ['糖尿病', input.topic],
    }
    callbacks.onChunk?.(draft)
    callbacks.onComplete?.(draft)
    return draft
  }
  const config = await getAiDraftConfig()
  return streamArticleDraft(config, input, callbacks)
}

export const ARTICLE_STATUS_LABELS = {
  draft: '草稿',
  pending: '待审核',
  published: '已发布',
  rejected: '已驳回',
}
