import { API_V2_BASE } from '@/config'

export function videoCoverUrl(videoId) {
  if (!videoId) return ''
  return `${API_V2_BASE}/home/media/video-cover/${videoId}.jpg`
}

export function videoFileUrl(videoId) {
  if (!videoId) return ''
  return `${API_V2_BASE}/home/media/video/${videoId}.mp4`
}
