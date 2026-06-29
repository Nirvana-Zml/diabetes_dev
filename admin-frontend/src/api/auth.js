import { post } from '@/utils/request'
import { saveUserRole } from '@/utils/auth'

/** 管理员登录 — 仅接受 role=admin 的账号 */
export function login(payload) {
  return post('/auth/login', payload, {
    mockFn: async () => {
      if (payload.username !== 'admin') {
        throw new Error('请使用管理员账号登录')
      }
      return {
        access_token: 'mock_admin_token_' + Date.now(),
        refresh_token: 'mock_admin_refresh_' + Date.now(),
        user: {
          user_id: 'adm_001',
          username: payload.username,
          nickname: '系统管理员',
          role: 'admin',
        },
        role: 'admin',
      }
    },
  })
}

export function saveTokens({ access_token, refresh_token, role, user }) {
  localStorage.setItem('access_token', access_token)
  if (refresh_token) localStorage.setItem('refresh_token', refresh_token)
  saveUserRole(role || user?.role || 'admin')
}

export function clearTokens() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user_role')
}
