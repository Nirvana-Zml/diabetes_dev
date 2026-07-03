import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  beforeEach: vi.fn(),
  afterEach: vi.fn(),
  isLoggedIn: vi.fn(() => true),
  isPublicRoute: vi.fn((route) => ['/login', '/register', '/forgot-password'].includes(route.path)),
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
  isPublicRoute: mocks.isPublicRoute,
  redirectToLogin: mocks.redirectToLogin,
}))

vi.mock('@/views/Assistant/index.vue', () => ({ default: { name: 'AssistantMock' } }))
vi.mock('@/views/CheckinAnalysis/index.vue', () => ({ default: { name: 'CheckinAnalysisMock' } }))
vi.mock('@/views/CheckinRecords/index.vue', () => ({ default: { name: 'CheckinRecordsMock' } }))
vi.mock('@/views/Consultation/index.vue', () => ({ default: { name: 'ConsultationMock' } }))
vi.mock('@/views/HealthEvaluation/index.vue', () => ({ default: { name: 'HealthEvaluationMock' } }))
vi.mock('@/views/HealthInfo/index.vue', () => ({ default: { name: 'HealthInfoMock' } }))
vi.mock('@/views/Home/index.vue', () => ({ default: { name: 'HomeMock' } }))
vi.mock('@/views/LivingPlans/index.vue', () => ({ default: { name: 'LivingPlansMock' } }))
vi.mock('@/views/ScienceVideos/index.vue', () => ({ default: { name: 'ScienceVideosMock' } }))
vi.mock('@/views/UserCenter/index.vue', () => ({ default: { name: 'UserCenterMock' } }))

describe('router modules', () => {
  it('exports all feature route records', async () => {
    const modules = await Promise.all([
      import('../modules/assistant'),
      import('../modules/checkinAnalysis'),
      import('../modules/checkinRecords'),
      import('../modules/consultation'),
      import('../modules/healthEvaluation'),
      import('../modules/healthInfo'),
      import('../modules/home'),
      import('../modules/livingPlans'),
      import('../modules/scienceVideos'),
      import('../modules/userCenter'),
    ])

    const routes = modules.flatMap((mod) => mod.default)
    expect(routes.map((route) => route.path)).toEqual(expect.arrayContaining([
      '/assistant',
      '/checkin-analysis',
      '/checkin-records',
      '/checkin-records/food',
      '/checkin-records/medication',
      '/checkin-records/exercise',
      '/checkin-records/glucose',
      '/checkin-records/achievements',
      '/consultation',
      '/health-evaluation',
      '/health-info',
      '/home',
      '/living-plans',
      '/science-videos',
      '/user-center',
    ]))

    for (const route of routes.filter((item) => typeof item.component === 'function')) {
      await expect(route.component()).resolves.toHaveProperty('default')
    }
  }, 15000)

  it('creates app router and registers auth guard', async () => {
    const router = (await import('../index')).default
    expect(router.options.routes.length).toBeGreaterThan(10)
    expect(mocks.beforeEach).toHaveBeenCalledWith(expect.any(Function))

    const guard = mocks.beforeEach.mock.calls[0][0]
    expect(guard({ path: '/register', fullPath: '/register' })).toBe(true)
    expect(guard({ path: '/login', fullPath: '/login' })).toBe(true)

    mocks.isLoggedIn.mockReturnValueOnce(true)
    expect(guard({ path: '/home', fullPath: '/home' })).toBe(true)

    mocks.isLoggedIn.mockReturnValueOnce(false)
    expect(guard({ path: '/user-center', fullPath: '/user-center?tab=profile' })).toEqual({
      path: '/login',
      query: { redirect: '/user-center?tab=profile' },
    })
    expect(mocks.redirectToLogin).toHaveBeenCalledWith('/user-center?tab=profile')

    expect(mocks.afterEach).toHaveBeenCalledWith(expect.any(Function))
    const afterEach = mocks.afterEach.mock.calls[0][0]
    afterEach({ meta: { title: '健康方案' } })
    expect(document.title).toBe('健康方案 - 糖尿病智能助手')

    afterEach({ meta: {} })
    expect(document.title).toBe('糖尿病智能助手 - 糖尿病智能助手')
  })
})
