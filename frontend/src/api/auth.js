/**
 * 认证 API — POST /api/v1/auth/*
 * Mock 模式下返回占位 token，不真实请求后端
 */
import { get, post } from '@/utils/request'

import { saveUserRole } from '@/utils/auth'

export function login(payload) {
  return post('/auth/login', payload, {
    mockFn: async () => ({
      access_token: 'mock_access_token_' + Date.now(),
      refresh_token: 'mock_refresh_token_' + Date.now(),
      user: {
        user_id: 'user_001',
        username: payload.username,
        nickname: payload.username,
        role: 'user',
      },
      role: 'user',
    }),
  })
}

export function register(payload) {
  return post('/auth/register', payload, {
    mockFn: async () => ({ user_id: 'user_new_' + Date.now(), username: payload.username }),
  })
}

export function resetPassword(payload) {
  return post('/auth/reset-password', payload, { mockFn: async () => ({ success: true }) })
}

export function sendVerifyCode(payload) {
  return post('/auth/send-code', payload, { mockFn: async () => ({ success: true, code: '123456' }) })
}

export function saveTokens({ access_token, refresh_token, role, user }) {
  localStorage.setItem('access_token', access_token)
  if (refresh_token) localStorage.setItem('refresh_token', refresh_token)
  saveUserRole(role || user?.role || 'user')
}

export function clearTokens() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user_role')
}

export function getProfile() {
  return get('/user/profile', {
    mockFn: async () => {
      const { mockUserProfile } = await import('@/mock/data')
      return mockUserProfile
    },
  })
}
