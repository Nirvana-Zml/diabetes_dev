import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/mock/data', () => ({
  mockUserProfile: { user_id: 'mock_user', nickname: '测试用户' },
}))

import { get, post } from '@/utils/request'
import {
  clearTokens,
  getProfile,
  login,
  register,
  resetPassword,
  saveTokens,
  sendVerifyCode,
} from '../auth'

describe('auth api', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('calls auth endpoints with mock functions', async () => {
    post.mockImplementation(async (_url, _payload, options) => options.mockFn())

    expect(await login({ username: 'tom' })).toMatchObject({
      access_token: expect.stringContaining('mock_access_token_'),
      user: { username: 'tom', role: 'user' },
    })
    expect(await register({ username: 'new' })).toMatchObject({ username: 'new' })
    expect(await resetPassword({ username: 'tom' })).toEqual({ success: true })
    expect(await sendVerifyCode({ username: 'tom' })).toEqual({ success: true })

    expect(post).toHaveBeenNthCalledWith(1, '/auth/login', { username: 'tom' }, expect.any(Object))
    expect(post).toHaveBeenNthCalledWith(2, '/auth/register', { username: 'new' }, expect.any(Object))
    expect(post).toHaveBeenNthCalledWith(3, '/auth/reset-password', { username: 'tom' }, expect.any(Object))
    expect(post).toHaveBeenNthCalledWith(4, '/auth/send-code', { username: 'tom' }, expect.any(Object))
  })

  it('saves and clears tokens', () => {
    saveTokens({ access_token: 'a1', refresh_token: 'r1', role: 'doctor' })
    expect(localStorage.getItem('access_token')).toBe('a1')
    expect(localStorage.getItem('refresh_token')).toBe('r1')
    expect(localStorage.getItem('user_role')).toBe('doctor')

    clearTokens()
    expect(localStorage.getItem('access_token')).toBeNull()
    expect(localStorage.getItem('refresh_token')).toBeNull()
    expect(localStorage.getItem('user_role')).toBeNull()
  })

  it('uses user role fallback when saving tokens', () => {
    saveTokens({ access_token: 'a1', user: { role: 'admin' } })
    expect(localStorage.getItem('user_role')).toBe('admin')

    saveTokens({ access_token: 'a2' })
    expect(localStorage.getItem('user_role')).toBe('user')
  })

  it('loads profile through request mock function', async () => {
    get.mockResolvedValue({ user_id: 'u1' })
    await expect(getProfile()).resolves.toEqual({ user_id: 'u1' })
    expect(get).toHaveBeenCalledWith('/user/profile', expect.objectContaining({ mockFn: expect.any(Function) }))
  })

  it('loads mock profile when request uses mock function', async () => {
    get.mockImplementation(async (_url, options) => options.mockFn())
    await expect(getProfile()).resolves.toEqual({ user_id: 'mock_user', nickname: '测试用户' })
  })
})
