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

describe('core business branch coverage', () => {
  it('covers article status normalization branches', async () => {
    const api = await import('../article')

    mocks.get.mockResolvedValueOnce({
      articles: [
        { articleId: 'a1', status: 'draft', category: 1 },
        { articleId: 'a2', status: 4, category: 5 },
        { articleId: 'a3', status: 9, category: 3 },
      ],
      total: 3,
    })
    const result = await api.getAdminArticles()
    expect(result.list[0].status).toBe('draft')
    expect(result.list[1].status).toBe('rejected')
    expect(result.list[2].status).toBe('draft')
  })

  it('covers article create/update mockFn branches when USE_MOCK is true', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'true')
    vi.resetModules()
    const api = await import('../article')

    mocks.post.mockImplementation(async (_url, _data, options) => options.mockFn())
    mocks.put.mockImplementation(async (_url, _data, options) => options.mockFn())

    await expect(api.createAdminArticle({ title: 'mock创建' })).resolves.toMatchObject({
      article_id: expect.stringContaining('art_new_'),
      status: 'draft',
    })
    await expect(api.updateAdminArticle('a1', { title: 'mock更新' })).resolves.toMatchObject({
      article_id: 'a1',
      status: 'draft',
    })
  })

  it('covers article create/update without mockFn when USE_MOCK is false', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.resetModules()
    const api = await import('../article')

    mocks.post.mockResolvedValueOnce({ articleId: 'live-new' })
    mocks.put.mockResolvedValueOnce({ articleId: 'live-a1' })

    await expect(api.createAdminArticle({ title: 'live' })).resolves.toMatchObject({ article_id: 'live-new' })
    await expect(api.updateAdminArticle('a1', { title: 'live' })).resolves.toMatchObject({ article_id: 'live-a1' })
  })

  it('covers video create/update mockFn branches', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'true')
    vi.resetModules()
    const video = await import('../video')

    mocks.post.mockImplementation(async (_url, _data, options) => options.mockFn())
    mocks.put.mockImplementation(async (_url, _data, options) => options.mockFn())

    await expect(video.createAdminVideo({ title: 'mock视频' })).resolves.toMatchObject({
      video_id: 'video_new',
      title: 'mock视频',
    })
    await expect(video.updateAdminVideo('v1', { title: '更新视频' })).resolves.toMatchObject({
      video_id: 'v1',
      title: '更新视频',
    })
  })

  it('covers audit log snake_case fallback branches', async () => {
    const audit = await import('../audit')
    mocks.get.mockResolvedValueOnce({
      logs: [{
        log_id: 'legacy-1',
        user_id: 'legacy-u',
        ip_address: '10.0.0.1',
        user_agent: 'legacy-agent',
        created_at: '2026-07-01',
      }],
      total: 1,
    })
    await expect(audit.getAdminAuditLogs()).resolves.toMatchObject({
      list: [expect.objectContaining({
        log_id: 'legacy-1',
        user_id: 'legacy-u',
        ip_address: '10.0.0.1',
      })],
    })

    mocks.get.mockResolvedValueOnce({
      logs: [{ logId: 'camel-1' }],
      total: 1,
    })
    await expect(audit.getAdminAuditLogs()).resolves.toMatchObject({
      list: [expect.objectContaining({ log_id: 'camel-1' })],
    })
  })

  it('covers video snake_case normalization branches', async () => {
    const video = await import('../video')
    mocks.get.mockResolvedValueOnce({
      videos: [{
        video_id: 'legacy-v1',
        cover_url: 'legacy-cover.jpg',
        video_url: 'legacy-video.mp4',
        created_at: '2026-07-01',
        updated_at: '2026-07-02',
      }],
      total: 1,
    })
    await expect(video.getAdminVideos()).resolves.toMatchObject({
      list: [expect.objectContaining({
        video_id: 'legacy-v1',
        cover_url: 'legacy-cover.jpg',
        video_url: 'legacy-video.mp4',
      })],
    })

    mocks.get.mockResolvedValueOnce({
      videos: [{ title: '无 ID 字段', duration: '01:00' }],
      total: 1,
    })
    await expect(video.getAdminVideos()).resolves.toMatchObject({
      list: [expect.objectContaining({ title: '无 ID 字段', duration: '01:00' })],
    })
  })

  it('covers auth token normalization branches', async () => {
    const auth = await import('../auth')
    auth.saveTokens({
      accessToken: 'camel-token',
      refreshToken: 'camel-refresh',
      role: 'admin',
      username: 'admin',
    })
    expect(localStorage.getItem('access_token')).toBe('camel-token')
    auth.clearTokens()
  })
})
