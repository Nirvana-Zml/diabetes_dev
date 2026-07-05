import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  ackReminder: vi.fn(async () => ({})),
  clickReminder: vi.fn(async () => ({})),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('@/api/checkin', () => ({
  getTodayStatus: vi.fn(async () => ({ today_checkins: [], total_points: 0 })),
  getCheckinStats: vi.fn(async () => ({ total_points: 0, streak_days: 0 })),
  getAchievementWall: vi.fn(async () => ({ achievements: [], total: 0, unlockedCount: 0 })),
}))

vi.mock('@/api/checkinReminder', () => ({
  ackReminder: mocks.ackReminder,
  clickReminder: mocks.clickReminder,
}))

vi.mock('@/composables/useCheckinReminder', () => ({
  pendingReminders: { value: [] },
  useCheckinReminder: () => ({ refresh: vi.fn() }),
}))

vi.mock('@/composables/useBreakpoints', () => ({
  useIsMobile: () => ({ value: false }),
}))

vi.mock('@/components/layout/SiteLayout.vue', () => ({
  default: { template: '<section><slot /></section>' },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

const stubs = ['router-link', 'el-button', 'el-card', 'el-progress', 'el-empty']

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('CheckinRecords hub branches', () => {
  it('covers progress edge cases and reminder handlers', async () => {
    const CheckinRecords = (await import('../index.vue')).default
    const wrapper = shallowMount(CheckinRecords, { global: { stubs, directives: { loading: {} } } })
    await flush()

    wrapper.vm.todayStatus = { today_checkins: [{ checkin_type: 'diet', completed: true }] }
    expect(wrapper.vm.todayTasksSummary.total).toBe(4)
    expect(wrapper.vm.todayProgress).toBe(25)

    wrapper.vm.achievementWall = { achievements: [], total: 0, unlockedCount: 0 }
    expect(wrapper.vm.achievementPercent).toBe(0)

    wrapper.vm.todayStatus = {
      today_checkins: [{ checkinType: 'exercise', completed: true }],
    }
    expect(wrapper.vm.isTypeCompleted('exercise')).toBe(true)

    wrapper.vm.todayStatus = { today_checkins: null }
    expect(wrapper.vm.todayProgress).toBe(0)
    expect(wrapper.vm.todayTasksSummary.total).toBe(4)

    wrapper.vm.todayStatus = {
      today_checkins: [{ checkin_type: 'diet', completed: true }],
    }
    expect(wrapper.vm.isTypeCompleted('diet')).toBe(true)
    expect(wrapper.vm.isTypeCompleted('unknown_key')).toBe(false)

    await wrapper.vm.goReminderTab({ tab: 'food', log_id: 'log-1' })
    mocks.clickReminder.mockRejectedValueOnce(new Error('ignore'))
    await wrapper.vm.goReminderTab({ logId: 'log-2', tab: 'glucose' })

    wrapper.vm.dismissBanner({ log_id: 'log-3' })
    mocks.ackReminder.mockRejectedValueOnce(new Error('ignore'))
    wrapper.vm.dismissBanner({ logId: 'log-4' })
    wrapper.vm.dismissBanner({})

    wrapper.unmount()
  })
})
