import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/mock/data', () => ({
  mockArticles: [
    { article_id: 'a1', title: '饮食建议', summary: '控糖饮食', category: 'diet' },
    { article_id: 'a2', title: '运动建议', summary: '规律运动', category: 'exercise' },
  ],
  mockArticleContent: '# 内容',
}))

import { get, post } from '@/utils/request'
import {
  getArticleDetail,
  getArticleFavorites,
  getArticles,
  getRecommendArticles,
  getRelatedArticles,
  reportArticleReadEvent,
  searchArticles,
  toggleArticleFavorite,
} from '../article'

describe('article api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('normalizes recommend and related article responses', async () => {
    get.mockResolvedValueOnce({
      articles: [{ articleId: 'a1', category: 2, recScore: 0.8, recReason: '匹配' }],
      total: 9,
      strategy: 'personalized',
      phase: 4,
    })
    await expect(getRecommendArticles({ page: 2, size: 3 })).resolves.toMatchObject({
      list: [{ article_id: 'a1', category: 'diet', rec_score: 0.8, rec_reason: '匹配' }],
      total: 9,
      strategy: 'personalized',
      phase: 4,
    })
    expect(get).toHaveBeenCalledWith('/articles/recommend', expect.objectContaining({
      params: { page: 2, size: 3 },
    }))

    get.mockResolvedValueOnce({ articles: [{ article_id: 'a2', category: 3 }], total: undefined })
    await expect(getRelatedArticles('a1')).resolves.toEqual({
      list: [{ article_id: 'a2', category: 'exercise', rec_score: undefined, rec_reason: undefined }],
      total: 1,
    })
  })

  it('normalizes list, detail, search and favorites', async () => {
    get.mockResolvedValueOnce({ list: [{ articleId: 'a1', category: 1 }], total: undefined })
    await expect(getArticles({ category: 'diet' })).resolves.toMatchObject({
      list: [{ article_id: 'a1', category: 'diabetes_basics' }],
      total: 1,
      page: 1,
    })
    expect(get).toHaveBeenLastCalledWith('/articles', expect.objectContaining({
      params: { category: 2, page: 1, size: 10 },
    }))

    get.mockResolvedValueOnce({ articleId: 'a1', category: 99, favorited: 1 })
    await expect(getArticleDetail('a1')).resolves.toMatchObject({
      article_id: 'a1',
      category: '99',
      favorited: true,
    })

    get.mockResolvedValueOnce({ articles: [{ article_id: 'a2', category: 'exercise' }] })
    await expect(searchArticles('运动')).resolves.toMatchObject({ list: [{ article_id: 'a2' }], total: 1 })

    get.mockResolvedValueOnce({ articles: [{ article_id: 'a1', category: 'diet' }], total: 8 })
    await expect(getArticleFavorites()).resolves.toMatchObject({ list: [{ article_id: 'a1' }], total: 8 })
  })

  it('reports read event and toggles favorite', async () => {
    post.mockResolvedValueOnce({ recorded: true })
    await expect(reportArticleReadEvent('a1', { durationSec: 12 })).resolves.toEqual({ recorded: true })
    expect(post).toHaveBeenCalledWith('/articles/a1/read-event', {
      duration_sec: 12,
      source: 'detail',
    }, expect.any(Object))

    post.mockResolvedValueOnce({ favorited: true, ignored: 'x' })
    await expect(toggleArticleFavorite('a1')).resolves.toEqual({ favorited: true })
    expect(post).toHaveBeenLastCalledWith('/articles/a1/favorite', {}, expect.any(Object))
  })

  it('executes mock fallback branches used in mock mode', async () => {
    get.mockImplementation(async (_url, options) => options.mockFn())
    post.mockImplementation(async (_url, _payload, options) => options.mockFn())

    await expect(getRecommendArticles()).resolves.toMatchObject({
      list: [
        { article_id: 'a1', rec_reason: '与您的饮食管理兴趣高度相关' },
        { article_id: 'a2', rec_reason: undefined },
      ],
      total: 2,
    })
    await expect(getRelatedArticles('a1', { size: 1 })).resolves.toMatchObject({
      list: [{ article_id: 'a2' }],
      total: 3,
    })
    await expect(getArticles({ category: 'diet', page: 2, size: 1 })).resolves.toMatchObject({
      list: [{ article_id: 'a1' }],
      page: 2,
    })
    await expect(getArticleDetail('missing')).resolves.toMatchObject({
      article_id: 'a1',
      content: '# 内容',
      favorited: false,
    })
    await expect(searchArticles('饮食', { page: 2, size: 5 })).resolves.toMatchObject({
      list: [{ article_id: 'a1' }],
    })
    await expect(getArticleFavorites({ page: 2, size: 1 })).resolves.toMatchObject({
      list: [{ article_id: 'a1' }, { article_id: 'a2' }],
      total: 2,
    })
    await expect(reportArticleReadEvent('a1', { duration_sec: 5, source: 'home' })).resolves.toEqual({
      recorded: true,
    })
    await expect(toggleArticleFavorite('a1')).resolves.toEqual({ favorited: true })
  })

  it('handles empty and default response fallbacks', async () => {
    get.mockResolvedValueOnce({ articles: undefined, total: undefined, strategy: '' })
    await expect(getRecommendArticles()).resolves.toMatchObject({
      list: [],
      total: 0,
      strategy: 'popular',
    })

    get.mockResolvedValueOnce({ articles: undefined, total: undefined })
    await expect(getRelatedArticles('a1')).resolves.toEqual({ list: [], total: 0 })

    post.mockResolvedValueOnce({ recorded: true })
    await expect(reportArticleReadEvent('a1')).resolves.toEqual({ recorded: true })
    expect(post).toHaveBeenLastCalledWith('/articles/a1/read-event', {
      duration_sec: 0,
      source: 'detail',
    }, expect.any(Object))

    get.mockResolvedValueOnce({})
    await expect(getArticles()).resolves.toMatchObject({ list: [], total: 0, page: 1 })

    get.mockResolvedValueOnce({})
    await expect(searchArticles('不存在')).resolves.toEqual({ list: [], total: 0 })

    get.mockResolvedValueOnce({ articles: undefined, total: undefined })
    await expect(getArticleFavorites()).resolves.toEqual({ list: [], total: 0 })
  })
})
