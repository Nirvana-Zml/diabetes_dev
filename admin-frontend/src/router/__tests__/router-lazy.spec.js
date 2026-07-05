import { describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/auth', () => ({
  isLoggedIn: vi.fn(() => true),
  isAdmin: vi.fn(() => true),
  redirectToLogin: vi.fn((redirect) => ({ path: '/login', query: { redirect } })),
}))

describe('router lazy imports', () => {
  it('loads all lazy route components', async () => {
    const router = (await import('../index')).default
    const loaders = router.options.routes
      .map((route) => route.component)
      .filter((component) => typeof component === 'function')

    expect(loaders.length).toBeGreaterThan(0)
    for (const load of loaders) {
      const mod = await load()
      expect(mod.default).toBeTruthy()
    }
  })
})
