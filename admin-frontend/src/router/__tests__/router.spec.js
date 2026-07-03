import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  beforeEach: vi.fn(),
  afterEach: vi.fn(),
  isLoggedIn: vi.fn(() => true),
  isAdmin: vi.fn(() => true),
  redirectToLogin: vi.fn((redirect) => ({ path: '/login', query: { redirect } })),
}))

vi.mock('vue-router', () => ({
  createWebHistory: vi.fn(() => ({ mode: 'history' })),
  createRouter: vi.fn((options) => ({
    options,
    beforeEach: mocks.beforeEach,
    afterEach: mocks.afterEach,
    currentRoute: { value: { fullPath: '/home' } },
    push: vi.fn(),
  })),
}))

vi.mock('@/utils/auth', () => ({
  isLoggedIn: mocks.isLoggedIn,
  isAdmin: mocks.isAdmin,
  redirectToLogin: mocks.redirectToLogin,
}))

vi.mock('@/views/Login/index.vue', () => ({ default: { name: 'LoginMock' } }))
vi.mock('@/views/Home/index.vue', () => ({ default: { name: 'HomeMock' } }))
vi.mock('@/views/Articles/index.vue', () => ({ default: { name: 'ArticlesMock' } }))
vi.mock('@/views/Statistics/index.vue', () => ({ default: { name: 'StatisticsMock' } }))
vi.mock('@/views/Videos/index.vue', () => ({ default: { name: 'VideosMock' } }))
vi.mock('@/views/AuditLogs/index.vue', () => ({ default: { name: 'AuditLogsMock' } }))

describe('router modules', () => {
  it('creates admin router with expected routes', async () => {
    const router = (await import('../index')).default
    const paths = router.options.routes.map((route) => route.path)
    expect(paths).toEqual(expect.arrayContaining([
      '/',
      '/login',
      '/home',
      '/articles',
      '/statistics',
      '/videos',
      '/audit-logs',
    ]))
  })

  it('registers auth guard and title hook', async () => {
    const router = (await import('../index')).default
    expect(mocks.beforeEach).toHaveBeenCalledWith(expect.any(Function))
    expect(mocks.afterEach).toHaveBeenCalledWith(expect.any(Function))

    const guard = mocks.beforeEach.mock.calls[0][0]

    const withAuth = (loggedIn, admin) => {
      mocks.isLoggedIn.mockReturnValue(loggedIn)
      mocks.isAdmin.mockReturnValue(admin)
    }

    withAuth(true, true)
    expect(guard({ path: '/login', fullPath: '/login' })).toEqual({ path: '/home', replace: true })

    withAuth(false, false)
    expect(guard({ path: '/login', fullPath: '/login' })).toBe(true)

    withAuth(true, true)
    expect(guard({ path: '/public-page', fullPath: '/public-page', meta: { public: true } })).toBe(true)

    withAuth(true, true)
    expect(guard({ path: '/articles', fullPath: '/articles', meta: {} })).toBe(true)

    withAuth(false, false)
    expect(guard({ path: '/articles', fullPath: '/articles?tab=1', meta: {} })).toEqual({
      path: '/login',
      query: { redirect: '/articles?tab=1' },
    })
    expect(mocks.redirectToLogin).toHaveBeenCalledWith('/articles?tab=1')

    withAuth(true, false)
    expect(guard({ path: '/home', fullPath: '/home', meta: {} })).toEqual({
      path: '/login',
      query: { redirect: '/home' },
    })

    const afterEach = mocks.afterEach.mock.calls[0][0]
    afterEach({ meta: { title: '资讯管理' } })
    expect(document.title).toBe('资讯管理 - 糖尿病智能助手')
    afterEach({ meta: {} })
    expect(document.title).toBe('管理后台 - 糖尿病智能助手')
  })
})
