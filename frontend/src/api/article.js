import { get, post } from '@/utils/request'
import { articleCategoryToInt, normalizeArticle } from '@/utils/normalize'
import { mockArticles, mockArticleContent } from '@/mock/data'

const categoryMap = {
  diabetes_basics: '糖尿病基础',
  diet: '饮食管理',
  exercise: '运动康复',
  medication: '用药指导',
  complications: '并发症',
}

export { categoryMap }

/** GET /api/articles/recommend — 登录后个性化推荐（含 Dify AI 重排 rec_reason），未登录为热门推荐 */
export async function getRecommendArticles(params = {}) {
  const data = await get('/articles/recommend', {
    params: { page: params.page || 1, size: params.size || 10 },
    mockFn: async () => ({
      articles: mockArticles.map((a, i) => ({
        ...a,
        rec_reason: i === 0 ? '与您的饮食管理兴趣高度相关' : undefined,
      })),
      total: mockArticles.length,
      strategy: 'personalized',
      phase: 4,
    }),
  })
  const list = (data.articles || []).map(normalizeRecommendArticle)
  return {
    list,
    total: data.total ?? list.length,
    strategy: data.strategy || 'popular',
    phase: data.phase,
  }
}

/** GET /api/articles/{id}/related */
export async function getRelatedArticles(articleId, params = {}) {
  const data = await get(`/articles/${articleId}/related`, {
    params: { size: params.size || 5 },
    mockFn: async () => ({
      articles: mockArticles.filter((a) => a.article_id !== articleId).slice(0, 3),
      total: 3,
      strategy: 'related',
    }),
  })
  const list = (data.articles || []).map(normalizeRecommendArticle)
  return { list, total: data.total ?? list.length }
}

/** POST /api/articles/{id}/read-event — 上报阅读时长 */
export function reportArticleReadEvent(articleId, payload = {}) {
  return post(`/articles/${articleId}/read-event`, {
    duration_sec: payload.duration_sec ?? payload.durationSec ?? 0,
    source: payload.source || 'detail',
  }, {
    mockFn: async () => ({ recorded: true }),
  })
}

function normalizeRecommendArticle(item) {
  const a = normalizeArticle(item)
  a.rec_score = item.recScore ?? item.rec_score
  a.rec_reason = item.recReason ?? item.rec_reason
  return a
}

/** GET /api/articles */
export async function getArticles(params = {}) {
  const data = await get('/articles', {
    params: {
      category: articleCategoryToInt(params.category),
      page: params.page || 1,
      size: params.size || 10,
    },
    mockFn: async () => {
      let list = [...mockArticles]
      if (params.category) list = list.filter((a) => a.category === params.category)
      return { articles: list, total: list.length }
    },
  })
  const articles = (data.articles || data.list || []).map(normalizeArticle)
  return { list: articles, total: data.total ?? articles.length, page: params.page || 1 }
}

/** GET /api/articles/{id} */
export async function getArticleDetail(id) {
  const data = await get(`/articles/${id}`, {
    mockFn: async () => {
      const article = mockArticles.find((a) => a.article_id === id) || mockArticles[0]
      return { ...article, content: mockArticleContent, favorited: false }
    },
  })
  const article = normalizeArticle(data)
  article.favorited = !!data.favorited
  return article
}

/** GET /api/articles/search */
export async function searchArticles(keyword, params = {}) {
  const data = await get('/articles/search', {
    params: { keyword, page: params.page || 1, size: params.size || 20 },
    mockFn: async () => ({
      articles: mockArticles.filter((a) => a.title.includes(keyword) || a.summary.includes(keyword)),
      total: 1,
    }),
  })
  const list = (data.articles || []).map(normalizeArticle)
  return { list, total: data.total ?? list.length }
}

/** GET /api/articles/favorites */
export async function getArticleFavorites(params = {}) {
  const data = await get('/articles/favorites', {
    params: { page: params.page || 1, size: params.size || 20 },
    mockFn: async () => ({ articles: mockArticles.slice(0, 2), total: 2 }),
  })
  const list = (data.articles || []).map(normalizeArticle)
  return { list, total: data.total ?? list.length }
}

/** POST /api/articles/{id}/favorite */
export function toggleArticleFavorite(id) {
  return post(`/articles/${id}/favorite`, {}, {
    mockFn: async () => ({ favorited: true }),
  }).then((res) => ({ favorited: res.favorited }))
}
