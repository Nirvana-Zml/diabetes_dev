import { beforeEach, describe, expect, it } from 'vitest'
import {
  isLoggedIn,
  isPublicRoute,
  redirectToLogin,
  saveUserRole,
  resolvePostLoginRedirect,
  isAuthPath,
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

  it('saves and clears user role', () => {
    saveUserRole('admin')
    expect(localStorage.getItem('user_role')).toBe('admin')
    saveUserRole()
    expect(localStorage.getItem('user_role')).toBeNull()
  })

  it('recognizes public routes by meta, exact path and child path', () => {
    expect(isPublicRoute({ path: '/private', meta: { public: true } })).toBe(true)
    expect(isPublicRoute({ path: '/home', meta: {} })).toBe(true)
    expect(isPublicRoute({ path: '/health-info/detail/1', meta: {} })).toBe(true)
    expect(isPublicRoute({ path: '/user-center', meta: {} })).toBe(false)
  })

  it('builds login redirect route', () => {
    expect(redirectToLogin('/user-center')).toEqual({
      path: '/login',
      query: { redirect: '/user-center' },
    })
    expect(redirectToLogin()).toEqual({ path: '/login', query: undefined })
  })

  it('resolves safe post-login redirect', () => {
    expect(resolvePostLoginRedirect('/user-center')).toBe('/user-center')
    expect(resolvePostLoginRedirect('/consultation/chat?doctor_id=d1')).toBe('/consultation/chat?doctor_id=d1')
    expect(resolvePostLoginRedirect(undefined)).toBe('/home')
    expect(resolvePostLoginRedirect('https://evil.com')).toBe('/home')
    expect(resolvePostLoginRedirect('//evil.com')).toBe('/home')
  })

  it('detects auth paths', () => {
    expect(isAuthPath('/login')).toBe(true)
    expect(isAuthPath('/login?redirect=/home')).toBe(true)
    expect(isAuthPath('/register')).toBe(true)
    expect(isAuthPath('/home')).toBe(false)
    expect(isAuthPath('/consultation')).toBe(false)
  })
})
