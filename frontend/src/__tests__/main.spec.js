import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const app = {
    use: vi.fn(() => app),
    mount: vi.fn(() => app),
  }
  return {
    app,
    createApp: vi.fn(() => app),
    createPinia: vi.fn(() => 'pinia'),
  }
})

vi.mock('vue', () => ({
  createApp: mocks.createApp,
}))

vi.mock('pinia', () => ({
  createPinia: mocks.createPinia,
}))

vi.mock('element-plus', () => ({
  default: 'element-plus',
}))

vi.mock('@/router', () => ({
  default: 'router',
}))

vi.mock('../App.vue', () => ({
  default: { name: 'AppMock', template: '<div />' },
}))

vi.mock('../styles/auth.css', () => ({}))
vi.mock('../styles/site.css', () => ({}))
vi.mock('../styles/app.css', () => ({}))
vi.mock('element-plus/dist/index.css', () => ({}))
vi.mock('element-plus/es/locale/lang/zh-cn', () => ({ default: {} }))

describe('main entry', () => {
  it('creates and mounts the Vue application', async () => {
    vi.resetModules()
    await import('../main')

    expect(mocks.createApp).toHaveBeenCalled()
    expect(mocks.createPinia).toHaveBeenCalled()
    expect(mocks.app.use).toHaveBeenCalledWith('pinia')
    expect(mocks.app.use).toHaveBeenCalledWith('router')
    expect(mocks.app.use).toHaveBeenCalledWith('element-plus', expect.any(Object))
    expect(mocks.app.mount).toHaveBeenCalledWith('#app')
  })
})
