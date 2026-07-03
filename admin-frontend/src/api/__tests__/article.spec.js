import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  streamArticleDraft: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  del: mocks.del,
}))

vi.mock('@/utils/difyArticleDraft', () => ({
  streamArticleDraft: mocks.streamArticleDraft,
}))

vi.mock('@/mock/data', () => ({
  mockArticles: [
    { article_id: 'art_001', title: '饮食建议', summary: '控糖饮食', category: 'diet' },
    { article_id: 'art_002', title: '运动建议', summary: '规律运动', category: 'exercise' },
  ],
  mockArticleContent: '# 内容',
}))

function invokeMock(_url, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

function invokeWrite(_url, _data, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockImplementation(invokeMock)
  mocks.post.mockImplementation(invokeWrite)
  mocks.put.mockImplementation(invokeWrite)
  mocks.del.mockImplementation(invokeMock)
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('article api', () => {
  it('normalizes admin article list and detail', async () => {
    const api = await import('../article')

    mocks.get.mockResolvedValueOnce({
      articles: [{ articleId: 'a1', category: 2, status: 3, rejectReason: '格式问题' }],
      total: 1,
    })
    await expect(api.getAdminArticles({ status: 'published', keyword: '饮食', page: 2, size: 5 })).resolves.toMatchObject({
      list: [expect.objectContaining({
        article_id: 'a1',
        category: 'diet',
        status: 'published',
        reject_reason: '格式问题',
      })],
      total: 1,
    })

    mocks.get.mockResolvedValueOnce({
      articleId: 'a2',
      category: 'exercise',
      status: 2,
      createdAt: '2026-07-01',
      updatedAt: '2026-07-02',
    })
    await expect(api.getAdminArticleDetail('a2')).resolves.toMatchObject({
      article_id: 'a2',
      category: 'exercise',
      status: 'pending',
      created_at: '2026-07-01',
      updated_at: '2026-07-02',
    })
  })

  it('creates, updates, deletes and reviews articles', async () => {
    const api = await import('../article')

    mocks.post.mockResolvedValueOnce({ articleId: 'new', status: 'draft' })
    await expect(api.createAdminArticle({ title: '标题' })).resolves.toMatchObject({ article_id: 'new' })

    mocks.put.mockResolvedValueOnce({ articleId: 'a1', status: 'draft' })
    await expect(api.updateAdminArticle('a1', { title: '更新' })).resolves.toMatchObject({ article_id: 'a1' })

    mocks.post.mockResolvedValueOnce({ coverUrl: '/cover.jpg' })
    await expect(api.uploadAdminArticleCover('a1', new File(['x'], 'cover.jpg'))).resolves.toMatchObject({ cover_url: '/cover.jpg' })

    mocks.del.mockResolvedValueOnce('删除成功')
    await expect(api.deleteAdminArticle('a1')).resolves.toBe('删除成功')

    mocks.put.mockResolvedValueOnce({ articleId: 'a1', status: 'pending' })
    await expect(api.submitAdminArticle('a1')).resolves.toMatchObject({ status: 'pending' })

    mocks.put.mockResolvedValueOnce({ articleId: 'a1', status: 'published' })
    await expect(api.reviewAdminArticle('a1', 'approve')).resolves.toMatchObject({ status: 'published' })

    mocks.put.mockResolvedValueOnce({ articleId: 'a1', status: 'rejected' })
    await expect(api.reviewAdminArticle('a1', 'reject', '内容不完整')).resolves.toMatchObject({ status: 'rejected' })
  })

  it('loads pending review articles and ai draft config', async () => {
    const api = await import('../article')

    mocks.get.mockResolvedValueOnce({ articles: [{ articleId: 'a1', status: 2 }], total: 1 })
    await expect(api.getPendingReviewArticles({ page: 1, size: 10 })).resolves.toMatchObject({
      list: [expect.objectContaining({ article_id: 'a1', status: 'pending' })],
      total: 1,
    })

    mocks.get.mockResolvedValueOnce({
      workflowUrl: '/dify-proxy/v1/workflows/run',
      apiKey: 'app-key',
    })
    await expect(api.getAiDraftConfig()).resolves.toMatchObject({ workflowUrl: '/dify-proxy/v1/workflows/run' })
  })

  it('executes mock fallback branches', async () => {
    const api = await import('../article')

    await expect(api.getAdminArticles({ status: 'draft' })).resolves.toMatchObject({
      list: expect.arrayContaining([
        expect.objectContaining({ article_id: 'art_001', status: 'published' }),
        expect.objectContaining({ article_id: 'art_002', status: 'draft' }),
      ]),
      total: 2,
    })

    await expect(api.getAdminArticleDetail('missing')).resolves.toMatchObject({
      article_id: 'art_001',
      content: '# 内容',
      status: 'draft',
    })

    await expect(api.getPendingReviewArticles()).resolves.toMatchObject({
      list: [expect.objectContaining({ article_id: 'art_001' })],
      total: 1,
    })

    await expect(api.getAiDraftConfig()).resolves.toMatchObject({
      workflowUrl: '/dify-proxy/v1/workflows/run',
      apiKey: 'app-mock',
    })
  })

  it('generates article draft in mock mode', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'true')
    vi.resetModules()
    const api = await import('../article')
    const chunks = []
    const completed = []

    const draft = await api.generateArticleDraft({ topic: '控糖饮食' }, {
      onChunk: (value) => chunks.push(value),
      onComplete: (value) => completed.push(value),
    })

    expect(draft.title).toContain('控糖饮食')
    expect(chunks.length).toBeGreaterThan(0)
    expect(completed.length).toBeGreaterThan(0)
  })

  it('generates article draft through dify stream in live mode', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.resetModules()
    const api = await import('../article')
    mocks.get.mockResolvedValueOnce({
      workflowUrl: '/dify-proxy/v1/workflows/run',
      apiKey: 'app-key',
    })
    mocks.streamArticleDraft.mockResolvedValueOnce({ title: 'AI标题', content: 'AI正文' })

    await expect(api.generateArticleDraft({ topic: '运动' })).resolves.toMatchObject({
      title: 'AI标题',
      content: 'AI正文',
    })
    expect(mocks.streamArticleDraft).toHaveBeenCalled()
  })

  it('handles empty response fallbacks', async () => {
    const api = await import('../article')
    mocks.get.mockResolvedValueOnce({ articles: undefined, total: undefined })
    await expect(api.getAdminArticles()).resolves.toMatchObject({ list: [], total: 0 })

    mocks.get.mockResolvedValueOnce({ articles: undefined, total: undefined })
    await expect(api.getPendingReviewArticles()).resolves.toMatchObject({ list: [], total: 0 })
  })
})
