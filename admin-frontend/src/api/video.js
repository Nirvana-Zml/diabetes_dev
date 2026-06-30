import { get, post, put, del } from '@/utils/request'
import { USE_MOCK } from '@/config'
import { toCamelCase, toSnakeCase } from '@/utils/normalize'
import { mockVideos } from '@/mock/data'

function normalizeVideo(item) {
  const v = toSnakeCase(item)
  v.video_id = item.videoId || item.video_id || v.video_id
  v.cover_url = item.coverUrl || item.cover_url || v.cover_url
  v.video_url = item.videoUrl || item.video_url || v.video_url
  v.created_at = item.createdAt || item.created_at
  v.updated_at = item.updatedAt || item.updated_at
  return v
}

export async function getAdminVideos(params = {}) {
  const data = await get('/admin/videos', {
    params: {
      keyword: params.keyword,
      page: params.page || 1,
      size: params.size || 20,
    },
    mockFn: async () => ({
      videos: mockVideos.map((v) => ({
        videoId: v.video_id,
        title: v.title,
        duration: v.duration,
        coverUrl: v.cover_url,
        videoUrl: v.video_url,
      })),
      total: mockVideos.length,
    }),
  })
  const list = (data.videos || []).map(normalizeVideo)
  return { list, total: data.total ?? list.length }
}

export async function getAdminVideoDetail(id) {
  const data = await get(`/admin/videos/${id}`, {
    mockFn: async () => {
      const video = mockVideos.find((v) => v.video_id === id) || mockVideos[0]
      return {
        videoId: video.video_id,
        title: video.title,
        duration: video.duration,
        coverUrl: video.cover_url,
        videoUrl: video.video_url,
      }
    },
  })
  return normalizeVideo(data)
}

export function createAdminVideo(payload) {
  return post('/admin/videos', toCamelCase(payload), {
    mockFn: USE_MOCK ? async () => ({ videoId: 'video_new', title: payload.title }) : undefined,
  }).then(toSnakeCase)
}

export function updateAdminVideo(id, payload) {
  return put(`/admin/videos/${id}`, toCamelCase(payload), {
    mockFn: USE_MOCK ? async () => ({ videoId: id, title: payload.title }) : undefined,
  }).then(toSnakeCase)
}

export function deleteAdminVideo(id) {
  return del(`/admin/videos/${id}`, {
    mockFn: async () => '删除成功',
  })
}

/** POST /api/v1/admin/videos/{id}/cover — 上传封面至 MinIO video-cover/{id}.jpg */
export function uploadAdminVideoCover(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return post(`/admin/videos/${id}/cover`, formData).then(toSnakeCase)
}

/** POST /api/v1/admin/videos/{id}/file — 上传 MP4 至 MinIO video/{id}.mp4，后端解析时长 */
export function uploadAdminVideoFile(id, file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return post(`/admin/videos/${id}/file`, formData, {
    timeout: 600000,
    onUploadProgress: onProgress,
  }).then(toSnakeCase)
}
