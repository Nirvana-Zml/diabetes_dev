import { get, put, post } from '@/utils/request'
import { toSnakeCase } from '@/utils/normalize'
import {
  mockUserProfile,
  mockConsultationRecords,
  mockHealthAlert,
  mockHealthTrendSummary,
} from '@/mock/data'
import { getHealthRecord, updateHealthRecord } from '@/api/healthRecord'

/** GET /api/user/profile — user-service */
export function getUserProfile() {
  return get('/user/profile', { mockFn: async () => mockUserProfile }).then((d) => toSnakeCase(d))
}

/** PUT /api/user/profile */
export function updateUserProfile(data) {
  const payload = { ...data }
  if (typeof payload.gender === 'string') {
    payload.gender = GENDER_TO_INT[payload.gender] ?? 0
  }
  return put('/user/profile', payload, {
    mockFn: async () => ({ ...mockUserProfile, ...data }),
  }).then((d) => toSnakeCase(d))
}

/** POST /api/user/avatar — 上传头像至 MinIO profile/{userId}.jpg */
export function uploadUserAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return post('/user/avatar', formData, {
    mockFn: async () => ({
      avatar_url: `${mockUserProfile.avatar_url}?v=${Date.now()}`,
    }),
  }).then((d) => toSnakeCase(d))
}

const GENDER_TO_INT = { male: 1, female: 2, unknown: 0 }

export { getHealthRecord, updateHealthRecord }

/** GET /api/user/consultations — 咨询模块未实现，仅占位 */
export async function getUserConsultations(params = {}) {
  const { delay } = await import('@/utils/delay')
  await delay()
  return {
    list: mockConsultationRecords,
    total: mockConsultationRecords.length,
    page: params.page || 1,
    page_size: params.page_size || 10,
  }
}

/** 异常指标预警 — 占位 */
export async function getHealthAlert() {
  const { delay } = await import('@/utils/delay')
  await delay()
  return mockHealthAlert
}

/** AI 健康趋势 — 占位 */
export async function getHealthTrendSummary() {
  const { delay } = await import('@/utils/delay')
  await delay()
  return { summary: mockHealthTrendSummary }
}

/** POST /api/user/export — 占位 */
export async function exportUserData(data) {
  const { delay } = await import('@/utils/delay')
  await delay()
  return {
    task_id: 'export_' + Date.now(),
    message: '导出任务已提交，完成后将通知下载',
  }
}

/** PUT /api/user/privacy */
export function updatePrivacySettings(data) {
  return put('/user/privacy', data, { mockFn: async () => data })
}

/** PUT /api/user/password */
export function changePassword(data) {
  return put('/user/password', data, { mockFn: async () => ({ success: true }) })
}

/** GET /api/user/overview — 积分等概览 */
export function getUserOverview() {
  return get('/user/overview', {
    mockFn: async () => ({
      user_id: mockUserProfile.user_id,
      nickname: mockUserProfile.nickname,
      avatar_url: mockUserProfile.avatar_url,
      points: mockUserProfile.points,
    }),
  }).then((d) => toSnakeCase(d))
}
