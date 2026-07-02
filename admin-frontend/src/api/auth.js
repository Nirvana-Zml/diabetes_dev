import { post } from '@/utils/request'
import { saveUserRole } from '@/utils/auth'

/** 管理员登录 — 调用 POST /api/v1/auth/login，仅接受 role=admin */
export function login(payload) {
  return post('/auth/login', payload)
}

function normalizeLoginPayload(data) {
  if (!data || typeof data !== 'object') return null
  const accessToken = data.access_token || data.accessToken
  if (!accessToken) return null
  return {
    access_token: accessToken,
    refresh_token: data.refresh_token || data.refreshToken || '',
    role: data.role || 'admin',
    username: data.username || '',
  }
}

export function saveTokens(data) {
  const tokens = normalizeLoginPayload(data)
  if (!tokens) {
    throw new Error('登录响应无效，未获取到 access_token')
  }
  localStorage.setItem('access_token', tokens.access_token)
  if (tokens.refresh_token) localStorage.setItem('refresh_token', tokens.refresh_token)
  saveUserRole(tokens.role)
  if (tokens.username) localStorage.setItem('admin_username', tokens.username)
}

export function clearTokens() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user_role')
  localStorage.removeItem('admin_username')
}

export function getAdminUsername() {
  return localStorage.getItem('admin_username') || ''
}
