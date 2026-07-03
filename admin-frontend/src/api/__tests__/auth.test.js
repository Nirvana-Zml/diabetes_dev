import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}))

import { post } from '@/utils/request'
import {
  clearTokens,
  getAdminUsername,
  login,
  saveTokens,
} from '../auth'

describe('auth api', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('calls admin login endpoint', async () => {
    post.mockResolvedValueOnce({ access_token: 'token', role: 'admin' })
    await expect(login({ username: 'admin', password: 'secret' })).resolves.toMatchObject({
      access_token: 'token',
      role: 'admin',
    })
    expect(post).toHaveBeenCalledWith('/auth/login', { username: 'admin', password: 'secret' })
  })

  it('saves and clears tokens with camelCase fallback', () => {
    saveTokens({
      accessToken: 'a1',
      refreshToken: 'r1',
      role: 'admin',
      username: 'admin',
    })
    expect(localStorage.getItem('access_token')).toBe('a1')
    expect(localStorage.getItem('refresh_token')).toBe('r1')
    expect(localStorage.getItem('user_role')).toBe('admin')
    expect(localStorage.getItem('admin_username')).toBe('admin')
    expect(getAdminUsername()).toBe('admin')

    clearTokens()
    expect(localStorage.getItem('access_token')).toBeNull()
    expect(localStorage.getItem('refresh_token')).toBeNull()
    expect(localStorage.getItem('user_role')).toBeNull()
    expect(localStorage.getItem('admin_username')).toBeNull()
    expect(getAdminUsername()).toBe('')
  })

  it('uses default admin role when missing', () => {
    saveTokens({ access_token: 'a2' })
    expect(localStorage.getItem('user_role')).toBe('admin')
  })

  it('throws when login payload is invalid', () => {
    expect(() => saveTokens(null)).toThrow('登录响应无效')
    expect(() => saveTokens({ refresh_token: 'r1' })).toThrow('登录响应无效')
  })
})
