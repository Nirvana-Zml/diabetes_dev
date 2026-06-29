import { defineStore } from 'pinia'
import { getUserProfile } from '@/api/user'
import { clearTokens } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    profile: null,
    healthRecord: null,
    loaded: false,
  }),
  getters: {
    isLoggedIn: () => !!localStorage.getItem('access_token'),
    nickname: (s) => s.profile?.nickname || s.profile?.username || '用户',
    points: (s) => s.profile?.points ?? 0,
    streakDays: (s) => s.profile?.streak_days ?? 0,
  },
  actions: {
    async fetchProfile() {
      if (!this.isLoggedIn) return
      try {
        this.profile = await getUserProfile()
        this.loaded = true
      } catch {
        this.profile = null
      }
    },
    logout() {
      clearTokens()
      this.profile = null
      this.healthRecord = null
      this.loaded = false
    },
  },
})
