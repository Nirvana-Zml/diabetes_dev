/**
 * 科普首页 API
 */
import { getV2 } from '@/utils/request'
import { toSnakeCase } from '@/utils/normalize'
import { bannerImageUrl, videoCoverUrl, videoFileUrl, doctorAvatarUrl } from '@/utils/media'
import { mockBanners, mockCategories, mockVideos, mockDoctors } from '@/mock/data'
import { getPopularArticles } from '@/api/article'

const DOCTOR_STATUS_MAP = {
  1: 'online',
  2: 'offline',
  3: 'busy',
  online: 'online',
  offline: 'offline',
  busy: 'busy',
}

export function normalizeDoctor(item) {
  if (!item) return item
  const d = toSnakeCase(item)
  const rawStatus = d.status ?? item.status ?? 'offline'
  return {
    doctor_id: d.doctor_id ?? item.doctorId ?? item.doctor_id ?? '',
    name: d.name ?? '',
    title: d.title ?? '',
    department: d.department ?? '',
    hospital: d.hospital ?? '',
    avatar_url: d.avatar_url ?? d.avatar ?? doctorAvatarUrl(d.doctor_id ?? item.doctorId ?? ''),
    rating: Number(d.rating ?? 0),
    consultation_count: d.consultation_count ?? d.consultationCount ?? 0,
    status: DOCTOR_STATUS_MAP[rawStatus] ?? String(rawStatus),
    introduction: d.introduction ?? '',
  }
}

function filterDoctors(list, params = {}) {
  let result = [...list]
  if (params.department) {
    result = result.filter((d) => d.department === params.department)
  }
  if (params.status) {
    const status = DOCTOR_STATUS_MAP[params.status] ?? params.status
    result = result.filter((d) => d.status === status)
  }
  if (params.keyword?.trim()) {
    const kw = params.keyword.trim()
    result = result.filter(
      (d) =>
        d.name.includes(kw) ||
        d.department.includes(kw) ||
        (d.hospital && d.hospital.includes(kw)),
    )
  }
  return result
}

function formatDuration(raw) {
  if (!raw) return ''
  const text = String(raw)
  const parts = text.split(':')
  if (parts.length === 3) {
    const hour = Number(parts[0])
    if (hour > 0) {
      return `${hour}:${parts[1]}:${parts[2]}`
    }
    return `${Number(parts[1])}:${parts[2]}`
  }
  return text
}

function resolveBannerId(item) {
  return item.banner_id ?? item.bannerId ?? item.id ?? ''
}

function normalizeBanner(item) {
  const id = resolveBannerId(item)
  return {
    id,
    title: item.title ?? '',
    image: item.image ?? item.image_url ?? item.imageUrl ?? bannerImageUrl(id),
  }
}

export function normalizeVideo(item) {
  const id = item.video_id ?? item.videoId ?? item.id ?? ''
  return {
    id,
    title: item.title ?? '',
    cover: item.cover ?? item.cover_url ?? item.coverUrl ?? videoCoverUrl(id),
    url: item.url ?? item.video_url ?? item.videoUrl ?? videoFileUrl(id),
    duration: formatDuration(item.duration),
  }
}

/** GET /api/v2/home/content — 科普视频列表（含搜索） */
export async function getVideos(params = {}) {
  const { videos } = await getHomeContent()
  let list = videos
  const keyword = params.keyword?.trim()
  if (keyword) {
    list = list.filter((v) => v.title.includes(keyword))
  }
  return { list, total: list.length }
}

/** GET /api/v2/home/content — 轮播图与科普视频列表 */
export async function getHomeContent() {
  const data = await getV2('/home/content', {
    mockFn: async () => ({
      banners: mockBanners.map(({ id, title }) => ({ bannerId: id, title })),
      videos: mockVideos.map(({ id, title, duration }) => ({ videoId: id, title, duration })),
    }),
  })
  return {
    banners: (data.banners || []).map(normalizeBanner),
    videos: (data.videos || []).map(normalizeVideo),
    categories: data.categories || mockCategories,
  }
}

/** 首页资讯 — 始终使用热门推荐快速路径 */
export async function getHomeArticles(size = 4) {
  const data = await getPopularArticles({ size })
  return data.list
}

/** GET /api/v2/ai-doctors — 医师列表（首页预览与咨询页共用） */
function resolveDoctorRawList(data) {
  return Array.isArray(data) ? data : data?.doctors || data?.list || []
}

export async function getDoctors(params = {}) {
  const data = await getV2('/ai-doctors', {
    params: {
      department: params.department || undefined,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
    },
    mockFn: async () => mockDoctors,
  })
  const raw = resolveDoctorRawList(data)
  return filterDoctors(raw.map(normalizeDoctor), params)
}

/** @internal 供单元测试覆盖轮播图归一化分支 */
export function normalizeBannerForTest(item) {
  return normalizeBanner(item)
}

/** @internal 供单元测试覆盖字段回退分支 */
export function resolveBannerIdForTest(item) {
  return resolveBannerId(item)
}

export function resolveDoctorRawListForTest(data) {
  return resolveDoctorRawList(data)
}
