import { beforeEach, describe, expect, it } from 'vitest'
import {
  getUserRole,
  isAdmin,
  isLoggedIn,
  redirectToLogin,
  saveUserRole,
} from '../auth'

describe('auth utils', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('detects login state from access token', () => {
    expect(isLoggedIn()).toBe(false)
    localStorage.setItem('access_token', 'token')
    expect(isLoggedIn()).toBe(true)
  })

  it('reads and saves user role', () => {
    expect(getUserRole()).toBe('')
    saveUserRole('admin')
    expect(getUserRole()).toBe('admin')
    expect(isAdmin()).toBe(true)
    saveUserRole()
    expect(getUserRole()).toBe('')
    expect(isAdmin()).toBe(false)
  })

  it('builds login redirect route', () => {
    expect(redirectToLogin('/articles')).toEqual({
      path: '/login',
      query: { redirect: '/articles' },
    })
    expect(redirectToLogin()).toEqual({ path: '/login', query: undefined })
  })
})
