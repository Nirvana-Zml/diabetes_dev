import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  route: { meta: {} },
  userStore: { isLoggedIn: false },
  reminderStart: vi.fn(),
  reminderStop: vi.fn(),
  messageStart: vi.fn(),
  messageStop: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => mocks.userStore,
}))

vi.mock('@/composables/useCheckinReminder', () => ({
  useCheckinReminder: () => ({
    start: mocks.reminderStart,
    stop: mocks.reminderStop,
  }),
}))

vi.mock('@/composables/useMessageCenter', () => ({
  useMessageCenter: () => ({
    start: mocks.messageStart,
    stop: mocks.messageStop,
  }),
}))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.route.meta = {}
  mocks.userStore.isLoggedIn = false
})

describe('App', () => {
  it('uses auth layout class from route meta', async () => {
    mocks.route.meta = { layout: 'auth' }
    const App = (await import('../App.vue')).default
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: { template: '<main />' },
        },
      },
    })

    expect(wrapper.classes()).toContain('app-root')
    wrapper.unmount()
  })

  it('does not start background services when logged out', async () => {
    const App = (await import('../App.vue')).default
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: { template: '<main />' },
        },
      },
    })

    expect(mocks.reminderStart).not.toHaveBeenCalled()
    expect(mocks.messageStart).not.toHaveBeenCalled()

    wrapper.unmount()
    expect(mocks.reminderStop).not.toHaveBeenCalled()
    expect(mocks.messageStop).not.toHaveBeenCalled()
  })

  it('starts and stops reminder services when logged in', async () => {
    mocks.userStore.isLoggedIn = true
    const App = (await import('../App.vue')).default
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: { template: '<main />' },
        },
      },
    })

    expect(wrapper.classes()).toContain('app-main')
    expect(mocks.reminderStart).toHaveBeenCalledTimes(1)
    expect(mocks.messageStart).toHaveBeenCalledTimes(1)

    wrapper.unmount()

    expect(mocks.reminderStop).toHaveBeenCalledTimes(1)
    expect(mocks.messageStop).toHaveBeenCalledTimes(1)
  })
})
