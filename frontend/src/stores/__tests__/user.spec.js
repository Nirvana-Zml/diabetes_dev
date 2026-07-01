import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  getUserProfile: vi.fn(),
  clearTokens: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  getUserProfile: mocks.getUserProfile,
}))

vi.mock('@/api/auth', () => ({
  clearTokens: mocks.clearTokens,
}))

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.clearAllMocks()
})

describe('user store', () => {
  it('exposes defaults and fetches profile when logged in', async () => {
    const { useUserStore } = await import('../user')
    const store = useUserStore()

    expect(store.nickname).toBe('用户')
    expect(store.points).toBe(0)
    expect(store.streakDays).toBe(0)

    await store.fetchProfile()
    expect(mocks.getUserProfile).not.toHaveBeenCalled()

    setActivePinia(createPinia())
    const loggedStore = useUserStore()
    localStorage.setItem('access_token', 'token')
    mocks.getUserProfile.mockResolvedValue({ nickname: '张三', points: 88, streak_days: 6 })
    await loggedStore.fetchProfile()

    expect(loggedStore.loaded).toBe(true)
    expect(loggedStore.nickname).toBe('张三')
    expect(loggedStore.points).toBe(88)
    expect(loggedStore.streakDays).toBe(6)
  })

  it('handles profile load failure and logout', async () => {
    const { useUserStore } = await import('../user')
    const store = useUserStore()

    localStorage.setItem('access_token', 'token')
    mocks.getUserProfile.mockRejectedValue(new Error('failed'))
    await store.fetchProfile()
    expect(store.profile).toBeNull()

    store.profile = { username: 'u1' }
    store.healthRecord = { height: 170 }
    store.loaded = true
    store.logout()

    expect(mocks.clearTokens).toHaveBeenCalled()
    expect(store.profile).toBeNull()
    expect(store.healthRecord).toBeNull()
    expect(store.loaded).toBe(false)
  })
})
