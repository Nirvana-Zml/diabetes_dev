import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  assessRisk: vi.fn(),
  getRiskHistory: vi.fn(),
  getRiskDetail: vi.fn(),
  getUserProfile: vi.fn(),
  getHealthRecord: vi.fn(),
  userStore: { profile: { user_id: 'store-user' } },
  unreadCount: { __v_isRef: true, value: 0 },
  loadUnread: vi.fn(),
  isMobile: { __v_isRef: true, value: false },
  formValidate: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
  error: vi.fn(),
}))

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
  })),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    warning: mocks.warning,
    info: mocks.info,
    error: mocks.error,
  },
}))

vi.mock('@/components/layout/SiteLayout.vue', () => ({
  default: { name: 'SiteLayout', template: '<section><slot /></section>' },
}))

vi.mock('@/components/DisclaimerBar.vue', () => ({
  default: { name: 'DisclaimerBar', template: '<div />' },
}))

vi.mock('@/api/risk', () => ({
  assessRisk: mocks.assessRisk,
  getRiskHistory: mocks.getRiskHistory,
  getRiskDetail: mocks.getRiskDetail,
}))

vi.mock('@/api/user', () => ({
  getUserProfile: mocks.getUserProfile,
}))

vi.mock('@/api/healthRecord', () => ({
  getHealthRecord: mocks.getHealthRecord,
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => mocks.userStore,
}))

vi.mock('@/composables/useMessageCenter', () => ({
  useMessageCenter: () => ({
    unreadCount: mocks.unreadCount,
    loadUnread: mocks.loadUnread,
  }),
}))

vi.mock('@/composables/useBreakpoints', () => ({
  useIsMobile: () => mocks.isMobile,
}))

const stubs = [
  'el-button',
  'el-form-item',
  'el-input',
  'el-input-number',
  'el-radio',
  'el-radio-group',
  'el-checkbox',
  'el-checkbox-group',
  'el-select',
  'el-option',
  'el-alert',
  'el-tag',
  'el-progress',
  'el-empty',
  'el-card',
  'el-row',
  'el-col',
  'el-icon',
  'el-steps',
  'el-step',
]

const FormStub = {
  name: 'ElForm',
  template: '<form><slot /></form>',
  setup(_props, { expose }) {
    expose({ validate: mocks.formValidate })
  },
}

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

async function mountPage() {
  const Page = (await import('../index.vue')).default
  const wrapper = shallowMount(Page, {
    global: {
      stubs: {
        ...Object.fromEntries(stubs.map((name) => [name, true])),
        'el-form': FormStub,
      },
      directives: { loading: {} },
    },
  })
  await flush()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  mocks.userStore.profile = { user_id: 'store-user' }
  mocks.getUserProfile.mockResolvedValue({ user_id: 'risk-user', nickname: '用户' })
  mocks.getHealthRecord.mockResolvedValue({ height: 170, weight: 65 })
  mocks.getRiskHistory.mockResolvedValue({ list: [], total: 0 })
  mocks.getRiskDetail.mockResolvedValue({ assessment_id: 'risk-detail', risk_score: 66 })
  mocks.assessRisk.mockResolvedValue({ assessment_id: 'risk-new', risk_score: 60 })
  mocks.formValidate.mockResolvedValue(true)
})

describe('HealthEvaluation remaining branches', () => {
  it('covers unread and pending storage helpers', async () => {
    const wrapper = await mountPage()

    wrapper.vm.userProfile = { user_id: 'risk-user' }
    wrapper.vm.result = { assessment_id: 'risk-1' }
    wrapper.vm.historyList = [{ assessment_id: 'risk-1', assessed_at: '2026-07-03T10:00:00' }]

    localStorage.setItem('he_risk_risk-user_unread', JSON.stringify(['risk-1']))
    wrapper.vm.readUnreadIds()
    expect(wrapper.vm.isCurrentResultUnread).toBe(true)
    expect(wrapper.vm.isUnreadAssessment({ assessment_id: 'risk-1' })).toBe(true)
    expect(wrapper.vm.latestUnreadAssessment).toMatchObject({ assessment_id: 'risk-1' })

    localStorage.setItem('he_risk_risk-user_unread', '{bad json')
    wrapper.vm.readUnreadIds()
    expect(wrapper.vm.unreadAssessmentIds).toEqual([])

    localStorage.removeItem('he_risk_risk-user_pending_at')
    wrapper.vm.syncPendingFromStorage()
    expect(wrapper.vm.assessPending).toBe(false)

    localStorage.setItem('he_risk_risk-user_pending_at', 'bad')
    wrapper.vm.syncPendingFromStorage()
    expect(wrapper.vm.assessPending).toBe(false)

    localStorage.setItem('he_risk_risk-user_pending_at', String(Date.now()))
    wrapper.vm.syncPendingFromStorage()
    expect(wrapper.vm.assessPending).toBe(true)

    mocks.getRiskHistory.mockRejectedValueOnce(new Error('history failed'))
    await wrapper.vm.loadHistory()
    expect(wrapper.vm.historyList).toEqual([])

    const pendingAt = Date.now()
    localStorage.setItem('he_risk_risk-user_pending_at', String(pendingAt))
    wrapper.vm.assessPending = true
    mocks.getRiskHistory.mockResolvedValueOnce({
      list: [{ assessment_id: 'risk-new', assessed_at: new Date(pendingAt).toISOString() }],
    })
    await wrapper.vm.loadHistory()
    expect(wrapper.vm.isUnreadAssessment({ assessment_id: 'risk-new' })).toBe(true)

    wrapper.vm.switchToHistoryTab()
    expect(wrapper.vm.activeTab).toBe('history')
  })

  it('covers submit validation and pending short circuits', async () => {
    const wrapper = await mountPage()

    mocks.formValidate.mockResolvedValueOnce(false)
    await wrapper.vm.submitAssess()
    expect(mocks.warning).toHaveBeenCalledWith('请完善必填项（体征指标与家族史）')
    expect(wrapper.vm.currentStep).toBe(1)

  })
})
