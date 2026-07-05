import { beforeEach, describe, expect, it, vi } from 'vitest'

const app = {
  use: vi.fn(function use() { return this }),
  mount: vi.fn(),
}

vi.mock('vue', () => ({
  createApp: vi.fn(() => app),
}))

vi.mock('element-plus', () => ({
  default: {},
}))

vi.mock('element-plus/dist/index.css', () => ({}))
vi.mock('element-plus/es/locale/lang/zh-cn', () => ({ default: {} }))
vi.mock('../App.vue', () => ({ default: { name: 'AppMock' } }))
vi.mock('../router', () => ({ default: { name: 'RouterMock' } }))
vi.mock('../styles/auth.css', () => ({}))
vi.mock('../styles/admin.css', () => ({}))

describe('main entry', () => {
  beforeEach(() => {
    vi.resetModules()
    app.use.mockClear()
    app.mount.mockClear()
  })

  it('bootstraps vue app with router and element-plus', async () => {
    const { createApp } = await import('vue')
    await import('../main.js')
    expect(createApp).toHaveBeenCalled()
    expect(app.use).toHaveBeenCalledTimes(2)
    expect(app.mount).toHaveBeenCalledWith('#app')
  })
})
