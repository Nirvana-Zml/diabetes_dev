/**
 * MinIO 资源 URL 构建（经 /api/v2/home/media 代理）
 */
import { API_V2_BASE } from '@/config'

export const MEDIA_BUCKETS = {
  banner: 'banner',
  videoCover: 'video-cover',
  video: 'video',
  avatar: 'avatar',
}

/** 构建 MinIO 资源代理 URL，objectName 默认为 {id}.jpg */
export function resourceMediaUrl(bucket, id, ext = 'jpg') {
  if (!id) return ''
  const objectName = String(id).includes('.') ? id : `${id}.${ext}`
  return `${API_V2_BASE}/home/media/${bucket}/${objectName}`
}

export function bannerImageUrl(bannerId) {
  return resourceMediaUrl(MEDIA_BUCKETS.banner, bannerId)
}

export function videoCoverUrl(videoId) {
  return resourceMediaUrl(MEDIA_BUCKETS.videoCover, videoId)
}

export function videoFileUrl(videoId) {
  return resourceMediaUrl(MEDIA_BUCKETS.video, videoId, 'mp4')
}

/** AI 医生头像 — minio avatar bucket，文件名为 {doctorId}.jpg */
export function doctorAvatarUrl(doctorId) {
  return resourceMediaUrl(MEDIA_BUCKETS.avatar, doctorId)
}
