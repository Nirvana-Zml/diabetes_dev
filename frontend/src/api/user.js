import { get, put, post } from '@/utils/request'
import { toSnakeCase } from '@/utils/normalize'
import {
  mockUserProfile,
  mockHealthAlert,
  mockHealthTrendSummary,
} from '@/mock/data'
import { getHealthRecord, updateHealthRecord } from '@/api/healthRecord'
import { listConsultations } from '@/api/consultation'

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

/** GET /api/v2/consultations — 当前用户问诊记录 */
export function getUserConsultations(params = {}) {
  return listConsultations(params)
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

/** POST /api/user/export — 提交数据导出 */
export function exportUserData(data) {
  return post('/user/export', data, {
    mockFn: async () => ({
      task_id: 'export_mock',
      status: 'completed',
      message: '导出成功（Mock）',
      download_url: '',
    }),
  }).then((d) => toSnakeCase(d))
}

/** GET /api/user/export/{taskId} — 查询导出任务 */
export function getExportTask(taskId) {
  return get(`/user/export/${taskId}`, {
    mockFn: async () => ({
      task_id: taskId,
      status: 'completed',
      download_url: '',
    }),
  }).then((d) => toSnakeCase(d))
}

/** PUT /api/user/privacy */
export function updatePrivacySettings(data) {
  return put('/user/privacy', { settings: data }, { mockFn: async () => data })
}

/** PUT /api/user/password */
export function changePassword(data) {
  return put('/user/password', data, { mockFn: async () => ({ success: true }) })
}

/** PUT /api/user/account/email — 绑定邮箱（需验证码） */
export function bindEmail(data) {
  return put('/user/account/email', data, {
    mockFn: async () => {
      const { mockUserProfile } = await import('@/mock/data')
      return { ...mockUserProfile, email: data.email }
    },
  }).then((d) => toSnakeCase(d))
}

/** PUT /api/user/account/phone — 绑定手机号（需验证码） */
export function bindPhone(data) {
  return put('/user/account/phone', data, {
    mockFn: async () => {
      const { mockUserProfile } = await import('@/mock/data')
      return { ...mockUserProfile, phone: data.phone }
    },
  }).then((d) => toSnakeCase(d))
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
