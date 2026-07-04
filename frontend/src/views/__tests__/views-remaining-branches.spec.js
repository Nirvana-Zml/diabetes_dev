import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { computed, nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  route: { query: {} },
  isMobile: { __v_isRef: true, value: false },
  refreshMessages: vi.fn(),
  getManagementStats: vi.fn(),
  getManagementTrends: vi.fn(),
  getAiSummary: vi.fn(),
  exportReport: vi.fn(),
  getGlucoseRecords: vi.fn(),
  getGlucoseHistory: vi.fn(),
  createGlucoseCheckin: vi.fn(),
  getLatestPlan: vi.fn(),
  generatePlan: vi.fn(),
  getPlanHistory: vi.fn(),
  togglePlanFavorite: vi.fn(),
  getPlanDetail: vi.fn(),
  getUserProfile: vi.fn(),
  getHealthRecord: vi.fn(),
  getHealthAlert: vi.fn(),
  getHealthTrendSummary: vi.fn(),
  getUserConsultations: vi.fn(),
  updatePrivacySettings: vi.fn(),
  acknowledgeHealthIntervention: vi.fn(),
  getCheckinStats: vi.fn(),
  userStore: { profile: {} },
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  isNotificationSupported: vi.fn(),
  getNotificationPermission: vi.fn(),
  requestNotificationPermission: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
  createRouter: vi.fn(() => ({
    beforeEach: vi.fn(),
    afterEach: vi.fn(),
    push: mocks.push,
    currentRoute: { value: { path: '/', fullPath: '/' } },
  })),
  createWebHistory: vi.fn(() => ({})),
}))

vi.mock('@/router', () => ({
  default: {
    push: mocks.push,
    currentRoute: { value: { path: '/', fullPath: '/' } },
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.success,
    warning: mocks.warning,
    error: mocks.error,
    info: mocks.info,
  },
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve()),
  },
}))

vi.mock('@element-plus/icons-vue', () => ({
  ArrowRight: { name: 'ArrowRight', template: '<i />' },
  Edit: { name: 'Edit', template: '<i />' },
  Coin: { name: 'Coin', template: '<i />' },
  Calendar: { name: 'Calendar', template: '<i />' },
  Document: { name: 'Document', template: '<i />' },
  ChatLineRound: { name: 'ChatLineRound', template: '<i />' },
  Download: { name: 'Download', template: '<i />' },
  Lock: { name: 'Lock', template: '<i />' },
  InfoFilled: { name: 'InfoFilled', template: '<i />' },
  MagicStick: { name: 'MagicStick', template: '<i />' },
  Star: { name: 'Star', template: '<i />' },
  StarFilled: { name: 'StarFilled', template: '<i />' },
  Printer: { name: 'Printer', template: '<i />' },
  Dish: { name: 'Dish', template: '<i />' },
  FirstAidKit: { name: 'FirstAidKit', template: '<i />' },
  Odometer: { name: 'Odometer', template: '<i />' },
  TrendCharts: { name: 'TrendCharts', template: '<i />' },
}))

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
  })),
}))

vi.mock('@/components/layout/SiteLayout.vue', () => ({
  default: { name: 'SiteLayout', template: '<section><slot /></section>' },
}))

vi.mock('@/components/MarkdownContent.vue', () => ({
  default: { name: 'MarkdownContent', template: '<div />' },
}))

vi.mock('../UserCenter/components/ProfileEditDialog.vue', () => ({
  default: { name: 'ProfileEditDialog', template: '<div />' },
}))

vi.mock('../UserCenter/components/HealthRecordDialog.vue', () => ({
  default: { name: 'HealthRecordDialog', template: '<div />' },
}))

vi.mock('../UserCenter/components/ExportDialog.vue', () => ({
  default: { name: 'ExportDialog', template: '<div />' },
}))

vi.mock('../UserCenter/components/ConsultationDrawer.vue', () => ({
  default: { name: 'ConsultationDrawer', template: '<div />' },
}))

vi.mock('../UserCenter/components/SecurityDialog.vue', () => ({
  default: { name: 'SecurityDialog', template: '<div />' },
}))

vi.mock('@/composables/useMessageCenter', () => ({
  useMessageCenter: () => ({ refresh: mocks.refreshMessages }),
}))

vi.mock('@/composables/useBreakpoints', () => ({
  useIsMobile: () => mocks.isMobile,
}))

vi.mock('@/api/checkinManagement', () => ({
  getManagementStats: mocks.getManagementStats,
  getManagementTrends: mocks.getManagementTrends,
  getAiSummary: mocks.getAiSummary,
  exportReport: mocks.exportReport,
}))

const checkinDate = { __v_isRef: true, value: '2026-07-03' }
vi.mock('../CheckinRecords/composables/useCheckinDate', () => ({
  useCheckinDate: () => ({
    checkinDate,
    isToday: computed(() => checkinDate.value === '2026-07-03'),
    dateDisplay: computed(() => checkinDate.value),
    changeDate: vi.fn(),
    setCheckinDate: vi.fn((date) => {
      checkinDate.value = date
    }),
  }),
}))

vi.mock('@/api/checkin', () => ({
  getGlucoseRecords: mocks.getGlucoseRecords,
  getGlucoseHistory: mocks.getGlucoseHistory,
  createGlucoseCheckin: mocks.createGlucoseCheckin,
  getCheckinStats: mocks.getCheckinStats,
}))

vi.mock('@/api/plan', () => ({
  getLatestPlan: mocks.getLatestPlan,
  generatePlan: mocks.generatePlan,
  getPlanHistory: mocks.getPlanHistory,
  togglePlanFavorite: mocks.togglePlanFavorite,
  getPlanDetail: mocks.getPlanDetail,
}))

vi.mock('@/api/user', () => ({
  getUserProfile: mocks.getUserProfile,
  getHealthRecord: mocks.getHealthRecord,
  getHealthAlert: mocks.getHealthAlert,
  getHealthTrendSummary: mocks.getHealthTrendSummary,
  getUserConsultations: mocks.getUserConsultations,
  updatePrivacySettings: mocks.updatePrivacySettings,
  acknowledgeHealthIntervention: mocks.acknowledgeHealthIntervention,
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => mocks.userStore,
}))

vi.mock('@/composables/useHealthTrendCache', () => ({
  readHealthTrendCache: vi.fn(() => null),
  writeHealthTrendCache: vi.fn(),
  clearHealthTrendCache: vi.fn(),
}))

vi.mock('@/utils/notification', () => ({
  isNotificationSupported: mocks.isNotificationSupported,
  getNotificationPermission: mocks.getNotificationPermission,
  requestNotificationPermission: mocks.requestNotificationPermission,
}))

const stubs = [
  'el-button',
  'el-form',
  'el-form-item',
  'el-input',
  'el-input-number',
  'el-select',
  'el-option',
  'el-date-picker',
  'el-table',
  'el-table-column',
  'el-card',
  'el-tag',
  'el-progress',
  'el-empty',
  'el-switch',
  'el-icon',
  'el-alert',
  'el-drawer',
  'el-dialog',
  'el-popover',
]

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function mountView(component) {
  return shallowMount(component, {
    global: {
      stubs,
      directives: { loading: {} },
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  mocks.route.query = {}
  mocks.isMobile.value = false
  mocks.userStore.profile = {}
  checkinDate.value = '2026-07-03'
  mocks.getManagementStats.mockResolvedValue({
    total_checkins: 1,
    completion_rate: 1,
    streak_days: 1,
    total_points: 10,
  })
  mocks.getManagementTrends.mockResolvedValue({ dates: [], glucose: [] })
  mocks.getAiSummary.mockResolvedValue({ source: 'dify', ai_summary: 'AI' })
  mocks.exportReport.mockResolvedValue({ export_url: 'report.csv' })
  mocks.getGlucoseRecords.mockResolvedValue([])
  mocks.getGlucoseHistory.mockResolvedValue({ records: [], summary: {} })
  mocks.createGlucoseCheckin.mockResolvedValue({ points_earned: 10 })
  mocks.getLatestPlan.mockResolvedValue({ plan_id: 'p1', summary: '方案' })
  mocks.getPlanHistory.mockResolvedValue({ list: [], total: 0 })
  mocks.getPlanDetail.mockResolvedValue({ plan_id: 'p1', summary: '详情' })
  mocks.togglePlanFavorite.mockResolvedValue({ favorited: true })
  mocks.getUserProfile.mockResolvedValue({ user_id: 'u1', privacy_settings: {} })
  mocks.getHealthRecord.mockResolvedValue({ recorded_at: '2026-07-03' })
  mocks.getHealthAlert.mockResolvedValue({ has_alert: false })
  mocks.getHealthTrendSummary.mockResolvedValue({ summary: '稳定' })
  mocks.getUserConsultations.mockResolvedValue({ list: [], total: 0 })
  mocks.updatePrivacySettings.mockResolvedValue({})
  mocks.acknowledgeHealthIntervention.mockResolvedValue({})
  mocks.getCheckinStats.mockResolvedValue({ completion_rate: 1 })
  mocks.isNotificationSupported.mockReturnValue(true)
  mocks.getNotificationPermission.mockReturnValue('default')
  mocks.requestNotificationPermission.mockResolvedValue('granted')
})

describe('remaining view branch coverage', () => {
  it('covers CheckinAnalysis dify refresh branch', async () => {
    const CheckinAnalysis = (await import('../CheckinAnalysis/index.vue')).default
    const wrapper = mountView(CheckinAnalysis)
    await flush()

    mocks.getAiSummary.mockResolvedValueOnce({ source: 'dify', ai_summary: 'Dify' })
    await wrapper.vm.loadAiSummary({ period: 'weekly' }, 'weekly-key')

    expect(mocks.refreshMessages).toHaveBeenCalled()
  })

  it('covers LivingPlans mobile history and time fallback', async () => {
    mocks.isMobile.value = true
    const LivingPlans = (await import('../LivingPlans/index.vue')).default
    const wrapper = mountView(LivingPlans)
    await flush()

    const rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      cb()
      return 1
    })
    const scrollIntoView = vi.fn()
    vi.spyOn(document, 'getElementById').mockReturnValue({ scrollIntoView })

    wrapper.vm.scrollToSection('plan-history')
    expect(wrapper.vm.historyOpen).toBe(true)

    const toDateStringSpy = vi.spyOn(Date.prototype, 'toDateString').mockImplementationOnce(() => {
      throw new Error('bad date')
    })
    expect(wrapper.vm.formatShortTime('bad-date')).toBe('Invalid Date')

    toDateStringSpy.mockRestore()
    document.getElementById.mockRestore()
    rafSpy.mockRestore()
  })

  it('covers UserCenter notification, query scroll and trend branches', async () => {
    mocks.route.query = { section: 'checkin-notify' }
    mocks.isNotificationSupported.mockReturnValue(false)
    const UserCenter = (await import('../UserCenter/index.vue')).default
    const wrapper = mountView(UserCenter)
    const scrollIntoView = vi.fn()
    wrapper.vm.checkinNotifyRef = { scrollIntoView }
    await flush()

    expect(wrapper.vm.browserNotifyLabel).toBe('不支持')
    wrapper.unmount()

    mocks.isNotificationSupported.mockReturnValue(true)
    mocks.getNotificationPermission.mockReturnValue('granted')
    const grantedWrapper = mountView(UserCenter)
    await flush()
    expect(grantedWrapper.vm.browserNotifyLabel).toBe('已授权')
    grantedWrapper.unmount()

    mocks.getNotificationPermission.mockReturnValue('denied')
    const deniedWrapper = mountView(UserCenter)
    await flush()
    expect(deniedWrapper.vm.browserNotifyLabel).toBe('已拒绝')
    deniedWrapper.unmount()

    mocks.getNotificationPermission.mockReturnValue('default')
    const defaultWrapper = mountView(UserCenter)
    await flush()
    expect(defaultWrapper.vm.browserNotifyLabel).toBe('未授权')

    mocks.requestNotificationPermission.mockResolvedValueOnce('granted')
    await defaultWrapper.vm.enableBrowserNotify()
    mocks.requestNotificationPermission.mockResolvedValueOnce('denied')
    await defaultWrapper.vm.enableBrowserNotify()

    defaultWrapper.vm.profile = { user_id: 'u1' }
    mocks.getHealthTrendSummary.mockResolvedValueOnce({ summary: '趋势良好' })
    await defaultWrapper.vm.refreshHealthTrend({ force: true })
    expect(defaultWrapper.vm.trendSummary).toBe('趋势良好')

    mocks.getHealthTrendSummary.mockRejectedValueOnce(new Error('trend failed'))
    await defaultWrapper.vm.refreshHealthTrend({ force: true })
    expect(defaultWrapper.vm.trendLoading).toBe(false)
  })
})
