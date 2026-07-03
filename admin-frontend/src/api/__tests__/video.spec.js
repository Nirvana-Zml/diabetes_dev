import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  del: mocks.del,
}))

vi.mock('@/mock/data', () => ({
  mockVideos: [
    {
      video_id: 'video_001',
      title: '什么是糖尿病？',
      duration: '05:32',
      cover_url: 'cover1.jpg',
      video_url: 'video1.mp4',
    },
    {
      video_id: 'video_002',
      title: '如何正确测血糖',
      duration: '03:18',
      cover_url: 'cover2.jpg',
      video_url: 'video2.mp4',
    },
  ],
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

describe('video api', () => {
  it('normalizes admin video list and detail', async () => {
    const api = await import('../video')

    mocks.get.mockResolvedValueOnce({
      videos: [{
        videoId: 'v1',
        title: '视频标题',
        duration: '01:00',
        coverUrl: 'cover.jpg',
        videoUrl: 'video.mp4',
        createdAt: '2026-07-01',
        updatedAt: '2026-07-02',
      }],
      total: 1,
    })
    await expect(api.getAdminVideos({ keyword: '血糖', page: 2, size: 5 })).resolves.toMatchObject({
      list: [expect.objectContaining({
        video_id: 'v1',
        cover_url: 'cover.jpg',
        video_url: 'video.mp4',
        created_at: '2026-07-01',
        updated_at: '2026-07-02',
      })],
      total: 1,
    })

    mocks.get.mockResolvedValueOnce({
      videoId: 'v2',
      title: '详情',
      duration: '02:00',
      coverUrl: 'cover2.jpg',
      videoUrl: 'video2.mp4',
    })
    await expect(api.getAdminVideoDetail('v2')).resolves.toMatchObject({
      video_id: 'v2',
      title: '详情',
    })
  })

  it('creates, updates, deletes and uploads videos', async () => {
    const api = await import('../video')

    mocks.post.mockResolvedValueOnce({ videoId: 'new', title: '新视频' })
    await expect(api.createAdminVideo({ title: '新视频' })).resolves.toMatchObject({ video_id: 'new' })

    mocks.put.mockResolvedValueOnce({ videoId: 'v1', title: '更新' })
    await expect(api.updateAdminVideo('v1', { title: '更新' })).resolves.toMatchObject({ video_id: 'v1' })

    mocks.del.mockResolvedValueOnce('删除成功')
    await expect(api.deleteAdminVideo('v1')).resolves.toBe('删除成功')

    mocks.post.mockResolvedValueOnce({ coverUrl: 'cover.jpg' })
    await expect(api.uploadAdminVideoCover('v1', new File(['x'], 'cover.jpg'))).resolves.toMatchObject({ cover_url: 'cover.jpg' })

    const onProgress = vi.fn()
    mocks.post.mockResolvedValueOnce({ videoUrl: 'video.mp4', duration: '03:00' })
    await expect(api.uploadAdminVideoFile('v1', new File(['x'], 'video.mp4'), onProgress)).resolves.toMatchObject({
      video_url: 'video.mp4',
    })
    expect(mocks.post).toHaveBeenLastCalledWith('/admin/videos/v1/file', expect.any(FormData), expect.objectContaining({
      timeout: 600000,
      onUploadProgress: onProgress,
    }))
  })

  it('executes mock fallback branches', async () => {
    const api = await import('../video')

    await expect(api.getAdminVideos()).resolves.toMatchObject({
      list: [
        expect.objectContaining({ video_id: 'video_001' }),
        expect.objectContaining({ video_id: 'video_002' }),
      ],
      total: 2,
    })

    await expect(api.getAdminVideoDetail('missing')).resolves.toMatchObject({
      video_id: 'video_001',
      title: '什么是糖尿病？',
    })
  })

  it('handles empty response fallbacks', async () => {
    const api = await import('../video')
    mocks.get.mockResolvedValueOnce({ videos: undefined, total: undefined })
    await expect(api.getAdminVideos()).resolves.toMatchObject({ list: [], total: 0 })
  })
})
