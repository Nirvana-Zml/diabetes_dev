import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  generatePlan: vi.fn(),
  getLatestPlan: vi.fn(async () => null),
  getPlanHistory: vi.fn(async () => ({ list: [{ plan_id: 'p1', is_favorite: 0 }], total: 1 })),
  getPlanDetail: vi.fn(async () => ({ plan_id: 'p1' })),
  togglePlanFavorite: vi.fn(async () => ({ favorited: false })),
  refreshMessages: vi.fn(),
  isMobile: { __v_isRef: true, value: false },
}))

vi.mock('@/api/plan', () => ({
  generatePlan: mocks.generatePlan,
  getLatestPlan: mocks.getLatestPlan,
  getPlanDetail: mocks.getPlanDetail,
  getPlanHistory: mocks.getPlanHistory,
  togglePlanFavorite: mocks.togglePlanFavorite,
}))

vi.mock('@/composables/useMessageCenter', () => ({
  useMessageCenter: () => ({ refresh: mocks.refreshMessages }),
}))

vi.mock('@/composables/useBreakpoints', () => ({
  useIsMobile: () => mocks.isMobile,
}))

vi.mock('@/components/layout/SiteLayout.vue', () => ({
  default: { template: '<section><slot /></section>' },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))

const stubs = [
  'el-button', 'el-card', 'el-tag', 'el-empty', 'el-skeleton', 'el-progress',
  'el-icon', 'el-affix', 'el-drawer', 'el-collapse', 'el-collapse-item',
]

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.isMobile.value = false
  vi.spyOn(window, 'print').mockImplementation(() => {})
})

describe('LivingPlans branch coverage', () => {
  it('covers all generatePlan onStage branches and error path', async () => {
    const LivingPlans = (await import('../index.vue')).default
    const wrapper = shallowMount(LivingPlans, { global: { stubs, directives: { loading: {} } } })
    await flush()

    mocks.generatePlan.mockImplementationOnce(async ({ onStage }) => {
      onStage({ stage: 'calorie', daily_calories: 1800 })
      onStage({ stage: 'diet', content: { meal_plan: {} } })
      onStage({ stage: 'exercise', content: { items: [] } })
      onStage({ stage: 'rest', content: { wake_up: '07:00' } })
      onStage({ stage: 'medication', content: '遵医嘱' })
      onStage({ stage: 'medication', content: { note: 'object' } })
      onStage({ stage: 'unknown' })
      onStage({ stage: 'complete', plan_id: 'p9' })
    })
    await wrapper.vm.handleGenerate()
    expect(mocks.refreshMessages).toHaveBeenCalled()

    mocks.generatePlan.mockRejectedValueOnce(new Error('生成失败'))
    await wrapper.vm.handleGenerate()
    expect(mocks.refreshMessages).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('covers favorite toggle off, giLabel fallback and mobile history scroll', async () => {
    const LivingPlans = (await import('../index.vue')).default
    const wrapper = shallowMount(LivingPlans, { global: { stubs, directives: { loading: {} } } })
    await flush()

    wrapper.vm.plan = { plan_id: 'p1', is_favorite: 1, diet_plan: {}, exercise_plan: {}, rest_plan: {} }
    wrapper.vm.favoriteLoading = false
    mocks.togglePlanFavorite.mockResolvedValueOnce({ favorited: false })
    await wrapper.vm.handleFavorite()

    expect(wrapper.vm.giLabel('unknown')).toBe('unknown')
    expect(wrapper.vm.giLabel()).toBe('—')
    expect(wrapper.vm.intensityType('高强度')).toBe('danger')
    expect(wrapper.vm.intensityType('轻松')).toBe('success')

    mocks.isMobile.value = true
    const scrollIntoView = vi.fn()
    vi.spyOn(document, 'getElementById').mockReturnValue({ scrollIntoView })
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => { cb(); return 1 })
    wrapper.vm.scrollToSection('plan-history')
    expect(wrapper.vm.historyOpen).toBe(true)

    wrapper.vm.handlePrint()
    wrapper.unmount()
  })

  it('covers computed plan dimensions and preview display branches', async () => {
    const LivingPlans = (await import('../index.vue')).default
    const wrapper = shallowMount(LivingPlans, { global: { stubs, directives: { loading: {} } } })
    await flush()

    wrapper.vm.generating = true
    wrapper.vm.plan = null
    wrapper.vm.previewPlan = {}
    const partial = wrapper.vm.dimensions
    expect(partial.find((d) => d.key === 'diet').percent).toBe(40)
    expect(partial.find((d) => d.key === 'exercise').percent).toBe(60)
    expect(partial.find((d) => d.key === 'rest').percent).toBe(80)
    expect(partial.find((d) => d.key === 'med').percent).toBe(0)

    wrapper.vm.generating = false
    wrapper.vm.previewPlan = {
      diet_plan: { meal_plan: { breakfast: { foods: ['燕麦'], time: '08:00', total_calories: 300 } } },
      exercise_plan: { items: [{ name: '散步' }], weekly_goal: '每周3次' },
      rest_plan: { wake_up: '07:00', sleep: '22:00', glucose_monitor_times: ['早餐前'], routine_tips: ['早睡'] },
      medication_note: '遵医嘱',
    }
    expect(wrapper.vm.dimensions.every((d) => d.percent >= 0)).toBe(true)
    expect(wrapper.vm.mealEntries[0].label).toBeTruthy()
    expect(wrapper.vm.exerciseItems).toHaveLength(1)
    expect(wrapper.vm.hasRestPlan).toBe(true)
    expect(wrapper.vm.restTips).toEqual(['早睡'])
    expect(wrapper.vm.sectionNavItems.map((i) => i.id)).toContain('plan-med')
    expect(wrapper.vm.overallPlanPercent).toBeGreaterThan(0)

    wrapper.vm.previewPlan.exercise_plan = [{ name: '慢跑' }]
    expect(wrapper.vm.exerciseItems).toHaveLength(1)

    wrapper.vm.syncFavoriteInHistory('p1', true)
    expect(wrapper.vm.history[0].is_favorite).toBe(1)

    wrapper.unmount()
  })
})
