import { beforeEach, describe, expect, it, vi } from 'vitest'

const router = vi.hoisted(() => ({
  push: vi.fn(),
  resolve: vi.fn((loc) => ({ fullPath: '/resolved' })),
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn() },
}))

vi.mock('@/router', () => ({
  default: router,
}))

vi.mock('@/utils/auth', () => ({
  isLoggedIn: vi.fn(() => false),
  redirectToLogin: vi.fn((path) => ({ path: '/login', query: path ? { redirect: path } : undefined })),
  LOGIN_REQUIRED_MESSAGE: '请先登录',
}))

describe('loginGate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns true when already logged in', async () => {
    const auth = await import('@/utils/auth')
    auth.isLoggedIn.mockReturnValueOnce(true)
    const { requireLogin } = await import('../loginGate')
    expect(requireLogin('/user-center')).toBe(true)
    expect(router.push).not.toHaveBeenCalled()
  })

  it('warns and redirects with string path', async () => {
    const { ElMessage } = await import('element-plus')
    const { requireLogin } = await import('../loginGate')

    expect(requireLogin('/user-center')).toBe(false)
    expect(ElMessage.warning).toHaveBeenCalledWith('请先登录')
    expect(router.push).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/user-center' },
    })
  })

  it('supports route object and navigate=false', async () => {
    const { requireLogin } = await import('../loginGate')

    expect(requireLogin({ name: 'UserCenter' }, { navigate: false })).toBe(false)
    expect(router.push).not.toHaveBeenCalled()
    expect(router.resolve).not.toHaveBeenCalled()

    expect(requireLogin({ name: 'UserCenter' })).toBe(false)
    expect(router.resolve).toHaveBeenCalledWith({ name: 'UserCenter' })
    expect(router.push).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/resolved' },
    })

    expect(requireLogin()).toBe(false)
    expect(router.push).toHaveBeenLastCalledWith({ path: '/login', query: undefined })
  })
})
