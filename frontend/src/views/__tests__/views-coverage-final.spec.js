import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { computed, nextTick, ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
  route: reactiveRoute(),
  recording: { value: false },
  startVoice: vi.fn(),
  stopVoice: vi.fn(),
  chatQA: vi.fn(),
  voiceToText: vi.fn(),
  refreshMessages: vi.fn(),
  loadList: vi.fn(),
  messageList: { value: [] },
  unreadCount: { value: 0 },
  isMobile: { __v_isRef: true, value: false },
  toggleArticleAudio: vi.fn(),
  stopArticleAudio: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  isLoggedIn: vi.fn(() => false),
  redirectToLogin: vi.fn((path) => ({ path: '/login', query: { redirect: path } })),
  safeBack: vi.fn(),
  markMessageRead: vi.fn(),
  markAllMessagesRead: vi.fn(),
}))

function reactiveRoute() {
  return {
    path: '/home',
    fullPath: '/home',
    name: 'home',
    meta: { title: '首页' },
    params: {},
    query: {},
  }
}

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace, back: mocks.back }),
  useRoute: () => mocks.route,
  createRouter: vi.fn(() => ({
    beforeEach: vi.fn(),
    afterEach: vi.fn(),
    push: mocks.push,
    currentRoute: { value: mocks.route },
  })),
  createWebHistory: vi.fn(() => ({})),
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
  ArrowRight: { template: '<i />' },
  Bell: { template: '<i />' },
  Calendar: { template: '<i />' },
  ChatLineRound: { template: '<i />' },
  Coin: { template: '<i />' },
  DataAnalysis: { template: '<i />' },
  Document: { template: '<i />' },
  Download: { template: '<i />' },
  Edit: { template: '<i />' },
  InfoFilled: { template: '<i />' },
  Lock: { template: '<i />' },
  MagicStick: { template: '<i />' },
  Printer: { template: '<i />' },
  Star: { template: '<i />' },
  StarFilled: { template: '<i />' },
  TrendCharts: { template: '<i />' },
  User: { template: '<i />' },
  Lock: { template: '<i />' },
  Iphone: { template: '<i />' },
  Message: { template: '<i />' },
  Key: { template: '<i />' },
  Clock: { template: '<i />' },
  Dish: { template: '<i />' },
  FirstAidKit: { template: '<i />' },
  Odometer: { template: '<i />' },
}))

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    getDom: vi.fn(() => null),
  })),
}))

vi.mock('@/config', () => ({
  APP_NAME: '糖尿病智能助手',
  ADMIN_PORTAL_URL: 'http://admin.local',
  DISCLAIMER: '⚠️ 仅供参考',
  PLATFORM_INTRO: '平台简介',
}))

vi.mock('@/router', () => ({
  default: {
    push: mocks.push,
    resolve: vi.fn((target) => ({ fullPath: typeof target === 'string' ? target : target.path || '/home' })),
    currentRoute: { value: { path: '/home', fullPath: '/home' } },
  },
}))

vi.mock('@/utils/auth', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    isLoggedIn: (...args) => mocks.isLoggedIn(...args),
    redirectToLogin: (...args) => mocks.redirectToLogin(...args),
  }
})

vi.mock('@/utils/navigation', () => ({
  safeBack: mocks.safeBack,
}))

vi.mock('@/composables/useVoiceInput', () => ({
  useVoiceInput: () => ({
    recording: mocks.recording,
    start: mocks.startVoice,
    stop: mocks.stopVoice,
  }),
}))

vi.mock('@/composables/useMessageCenter', () => ({
  useMessageCenter: () => ({
    refresh: mocks.refreshMessages,
    loadList: mocks.loadList,
    messageList: mocks.messageList,
  }),
  unreadCount: mocks.unreadCount,
}))

vi.mock('@/composables/useBreakpoints', () => ({
  useIsMobile: () => mocks.isMobile,
}))

vi.mock('@/composables/useArticleAudio', () => ({
  useArticleAudio: () => ({
    loading: { value: false },
    playing: { value: false },
    toggle: mocks.toggleArticleAudio,
    stop: mocks.stopArticleAudio,
  }),
}))

vi.mock('@/composables/useHealthTrendCache', () => ({
  readHealthTrendCache: vi.fn(() => null),
  writeHealthTrendCache: vi.fn(),
  clearHealthTrendCache: vi.fn(),
}))

vi.mock('@/components/layout/SiteLayout.vue', () => ({
  default: { name: 'SiteLayout', template: '<section><slot /></section>' },
}))

vi.mock('@/components/MarkdownContent.vue', () => ({
  default: { name: 'MarkdownContent', template: '<div />' },
}))

vi.mock('@/components/VoiceStatusBar.vue', () => ({
  default: { name: 'VoiceStatusBar', template: '<div />' },
}))

vi.mock('@/api/chat', () => ({
  chatQA: mocks.chatQA,
  voiceToText: mocks.voiceToText,
}))

vi.mock('@/api/message', () => ({
  markMessageRead: mocks.markMessageRead,
  markAllMessagesRead: mocks.markAllMessagesRead,
}))

vi.mock('@/api/home', () => ({
  getHomeContent: vi.fn(async () => ({
    banners: [{ id: 'b1', title: 'banner', image: 'banner.png' }],
    videos: [{ id: 'v1', title: '运动科普', cover: 'cover.png', url: 'video.mp4' }],
    categories: [{ id: 'diet', name: '饮食' }],
  })),
  getHomeArticles: vi.fn(async () => [{ article_id: 'a1', title: '饮食建议', category: 'diet' }]),
  getDoctors: vi.fn(async () => [{ doctor_id: 'd1', name: '李医生', status: 'online' }]),
  getVideos: vi.fn(async () => ({ list: [], total: 0 })),
}))

vi.mock('@/api/article', () => ({
  categoryMap: { diet: '饮食营养', exercise: '运动康复' },
  getRecommendArticles: vi.fn(async () => ({ list: [], total: 0 })),
  getRelatedArticles: vi.fn(async () => ({ list: [], total: 0 })),
  reportArticleReadEvent: vi.fn(async () => ({ recorded: true })),
  getArticles: vi.fn(async () => ({ list: [{ article_id: 'a1', title: '文章' }], total: 1 })),
  getArticleDetail: vi.fn(async () => ({ article_id: 'a1', title: '详情', content: '正文', favorited: false })),
  searchArticles: vi.fn(async () => ({ list: [], total: 0 })),
  getArticleFavorites: vi.fn(async () => ({ list: [], total: 0 })),
  toggleArticleFavorite: vi.fn(async () => ({ favorited: true })),
}))

vi.mock('@/api/checkin', () => ({
  uploadCheckinImage: vi.fn(async () => ({ object_key: 'img-key', url: 'img.png' })),
  getFoodCategories: vi.fn(async () => []),
  getFoodPresets: vi.fn(async () => []),
  createFoodCheckin: vi.fn(async () => ({ points_earned: 10 })),
  getFoodRecords: vi.fn(async () => []),
  getMedicationPresets: vi.fn(async () => [{ drug_id: 'm1', name: '二甲双胍' }]),
  createMedicationCheckin: vi.fn(async () => ({ points_earned: 10 })),
  getMedicationRecords: vi.fn(async () => []),
  getExercisePresets: vi.fn(async () => [{ exercise_id: 'e1', name: '散步' }]),
  createExerciseCheckin: vi.fn(async () => ({ points_earned: 10 })),
  getExerciseRecords: vi.fn(async () => []),
  createGlucoseCheckin: vi.fn(async () => ({ points_earned: 15 })),
  getGlucoseHistory: vi.fn(async () => ({ records: [], summary: {} })),
  getGlucoseRecords: vi.fn(async () => []),
  getTodayStatus: vi.fn(async () => ({ today_checkins: [], total_points: 0 })),
  getCheckinStats: vi.fn(async () => ({ total_points: 0, streak_days: 0, completion_rate: 0.5 })),
  getAchievements: vi.fn(async () => []),
  getAchievementWall: vi.fn(async () => ({ achievements: [], total: 0, unlockedCount: 0 })),
}))

vi.mock('@/api/checkinReminder', () => ({
  ackReminder: vi.fn(async () => ({ ok: true })),
  clickReminder: vi.fn(async () => ({ ok: true })),
  getReminderRules: vi.fn(async () => []),
  getReminderDefaults: vi.fn(async () => [
    { checkin_type: 1, remind_time: '08:00:00', enabled: true, sort_order: 0 },
  ]),
  saveReminderRules: vi.fn(async (rules) => rules),
}))

vi.mock('@/api/checkinManagement', () => ({
  getManagementStats: vi.fn(async () => ({ total_checkins: 1 })),
  getManagementTrends: vi.fn(async () => ({
    glucose: [{ date: '2026-07-01', value: 6.1 }],
    food: [{ date: '2026-07-01', count: 2 }],
  })),
  getAiSummary: vi.fn(async () => ({ source: 'dify', ai_summary: '稳定' })),
  exportReport: vi.fn(async () => ({ export_url: 'report.csv' })),
}))

vi.mock('@/api/consultation', () => ({
  getDepartments: vi.fn(async () => [{ id: 'endo', name: '内分泌科' }]),
  listConsultations: vi.fn(async () => ({ list: [], total: 0 })),
  createConsultation: vi.fn(async () => ({ session_id: 's1' })),
  getConsultMessages: vi.fn(async () => []),
  sendConsultMessage: vi.fn(async () => ({ message_id: 'msg1' })),
  getAiSuggest: vi.fn(async () => ({})),
  closeConsultation: vi.fn(async () => ({ success: true })),
}))

vi.mock('@/api/plan', () => ({
  generatePlan: vi.fn(async ({ onStage } = {}) => {
    onStage?.({ stage: 'calorie', daily_calories: 1800 })
    onStage?.({ stage: 'diet', content: { meal_plan: { breakfast: { foods: ['燕麦'] } } } })
    onStage?.({ stage: 'exercise', content: { items: [{ name: '散步' }] } })
    onStage?.({ stage: 'rest', content: { wake_up: '07:00', sleep: '22:30' } })
    onStage?.({ stage: 'medication', content: '遵医嘱' })
    onStage?.({ stage: 'complete', plan_id: 'p1' })
  }),
  getLatestPlan: vi.fn(async () => ({ plan_id: 'p1', diet_plan: {}, exercise_plan: {}, rest_plan: {} })),
  getPlanDetail: vi.fn(async () => ({ plan_id: 'p1', summary: '详情' })),
  getPlanHistory: vi.fn(async () => ({ list: [{ plan_id: 'p1' }], total: 1 })),
  togglePlanFavorite: vi.fn(async () => ({ favorited: true })),
}))

vi.mock('@/api/risk', () => ({
  assessRisk: vi.fn(async () => ({ assessment_id: 'r1', risk_level: 'low' })),
  getRiskHistory: vi.fn(async () => ({ list: [], total: 0 })),
  getRiskDetail: vi.fn(async () => ({ assessment_id: 'r1', risk_score: 20 })),
}))

vi.mock('@/api/user', () => ({
  getUserProfile: vi.fn(async () => ({ user_id: 'u1', points: 100, privacy_settings: { checkin_notify: true } })),
  updateUserProfile: vi.fn(async (data) => data),
  uploadUserAvatar: vi.fn(async () => ({ avatar_url: 'avatar.png' })),
  getUserConsultations: vi.fn(async () => ({ list: [], total: 0 })),
  getHealthAlert: vi.fn(async () => ({ has_alert: false })),
  getHealthTrendSummary: vi.fn(async () => ({ summary: '稳定' })),
  acknowledgeHealthIntervention: vi.fn(async () => ({ ok: true })),
  exportUserData: vi.fn(async () => ({ download_url: 'report.pdf' })),
  getExportTask: vi.fn(async () => ({ status: 'completed' })),
  updatePrivacySettings: vi.fn(async (data) => data),
  changePassword: vi.fn(async () => ({ success: true })),
  bindEmail: vi.fn(async (data) => data),
  bindPhone: vi.fn(async (data) => data),
  getUserOverview: vi.fn(async () => ({})),
  getHealthRecord: vi.fn(async () => ({ height: 170, weight: 65 })),
  updateHealthRecord: vi.fn(async (data) => data),
}))

vi.mock('@/api/auth.js', () => ({
  login: vi.fn(async () => ({ access_token: 'token', role: 'user' })),
  register: vi.fn(async () => ({ user_id: 'u1' })),
  resetPassword: vi.fn(async () => ({ success: true })),
  sendVerifyCode: vi.fn(async () => ({ success: true })),
  saveTokens: vi.fn(),
  clearTokens: vi.fn(),
}))

vi.mock('@/api/healthRecord', () => ({
  getHealthRecord: vi.fn(async () => ({ height: 170, weight: 65 })),
  updateHealthRecord: vi.fn(async (data) => data),
}))

const checkinDate = { __v_isRef: true, value: '2026-07-03' }
vi.mock('@/views/CheckinRecords/composables/useCheckinDate', () => ({
  useCheckinDate: () => ({
    checkinDate,
    isToday: computed(() => checkinDate.value === '2026-07-03'),
    dateDisplay: computed(() => checkinDate.value),
    changeDate: vi.fn(),
    setCheckinDate: vi.fn((date) => { checkinDate.value = date }),
  }),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    profile: { user_id: 'u1', privacy_settings: { checkin_notify: true } },
    nickname: '测试用户',
    fetchProfile: vi.fn(),
  }),
}))

vi.mock('@/utils/notification', () => ({
  isNotificationSupported: vi.fn(() => true),
  getNotificationPermission: vi.fn(() => 'default'),
  requestNotificationPermission: vi.fn(async () => 'granted'),
}))

const stubs = [
  'router-link', 'router-view', 'el-affix', 'el-alert', 'el-avatar', 'el-badge',
  'el-button', 'el-card', 'el-carousel', 'el-carousel-item', 'el-checkbox',
  'el-checkbox-group', 'el-col', 'el-date-picker', 'el-descriptions', 'el-descriptions-item',
  'el-dialog', 'el-divider', 'el-drawer', 'el-empty', 'el-form', 'el-form-item',
  'el-icon', 'el-image', 'el-input', 'el-input-number', 'el-link', 'el-option',
  'el-pagination', 'el-popover', 'el-progress', 'el-radio', 'el-radio-button',
  'el-radio-group', 'el-rate', 'el-row', 'el-scrollbar', 'el-select', 'el-skeleton',
  'el-skeleton-item', 'el-slider', 'el-statistic', 'el-step', 'el-steps', 'el-switch',
  'el-tab-pane', 'el-table', 'el-table-column', 'el-tabs', 'el-tag', 'el-time-picker',
  'el-timeline', 'el-timeline-item', 'el-tooltip', 'el-upload',
]

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function mountView(component, props = {}) {
  setActivePinia(createPinia())
  return shallowMount(component, {
    props,
    global: {
      plugins: [createPinia()],
      stubs,
      directives: { loading: {} },
      mocks: { $route: mocks.route, $router: { push: mocks.push } },
    },
  })
}

function validFormRef() {
  return {
    validate: vi.fn(() => Promise.resolve(true)),
    validateField: vi.fn(() => Promise.resolve(true)),
    clearValidate: vi.fn(),
  }
}

function invalidFormRef() {
  return {
    validate: vi.fn(() => Promise.resolve(false)),
    validateField: vi.fn(() => Promise.resolve(false)),
    clearValidate: vi.fn(),
  }
}

function setupOf(wrapper) {
  return wrapper.vm.$?.setupState || wrapper.vm
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  sessionStorage.clear()
  mocks.route.path = '/home'
  mocks.route.fullPath = '/home'
  mocks.route.params = {}
  mocks.route.query = {}
  mocks.route.meta = { title: '首页' }
  mocks.isMobile.value = false
  mocks.recording.value = false
  mocks.isLoggedIn.mockReturnValue(false)
  mocks.messageList.value = []
  mocks.unreadCount.value = 0
  checkinDate.value = '2026-07-03'
  mocks.loadList.mockResolvedValue(undefined)
  mocks.refreshMessages.mockResolvedValue(undefined)
  mocks.markMessageRead.mockResolvedValue(undefined)
  mocks.markAllMessagesRead.mockResolvedValue(undefined)
})

describe('Assistant/index.vue', () => {
  it('covers toggleVoice, isThinking, send streaming callbacks', async () => {
    const { ElMessage } = await import('element-plus')
    const Assistant = (await import('../Assistant/index.vue')).default
    const wrapper = mountView(Assistant)
    await flush()
    const vm = wrapper.vm

    vm.streaming = true
    await vm.toggleVoice()
    expect(mocks.startVoice).not.toHaveBeenCalled()

    vm.streaming = false
    vm.transcribing = true
    await vm.toggleVoice()
    expect(mocks.startVoice).not.toHaveBeenCalled()

    vm.transcribing = false
    mocks.startVoice.mockRejectedValueOnce(new Error('denied'))
    await vm.toggleVoice()
    expect(ElMessage.warning).toHaveBeenCalledWith('无法访问麦克风，请检查浏览器权限')

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce(null)
    await vm.toggleVoice()
    expect(ElMessage.warning).toHaveBeenCalledWith('录音时间过短，请重试')

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['v'], { type: 'audio/webm' }))
    mocks.voiceToText.mockResolvedValueOnce({ text: ' 血糖问题 ' })
    vm.textareaRef = { style: {}, scrollHeight: 80 }
    await vm.toggleVoice()
    expect(vm.query).toBe(' 血糖问题 ')

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['v'], { type: 'audio/webm' }))
    mocks.voiceToText.mockRejectedValueOnce(new Error('识别失败'))
    await vm.toggleVoice()
    expect(ElMessage.error).toHaveBeenCalledWith('识别失败')
    expect(vm.transcribing).toBe(false)

    vm.messages = [
      { role: 'assistant', content: '欢迎' },
      { role: 'user', content: '问题' },
      { role: 'assistant', content: '' },
    ]
    vm.streaming = true
    expect(vm.isThinking(vm.messages[2], 1)).toBe(true)
    expect(vm.isThinking({ role: 'assistant', content: '有内容' }, 1)).toBe(false)

    const setup = setupOf(wrapper)
    setup.streaming = false
    setup.textareaRef = { style: {}, scrollHeight: 80 }
    setup.msgRef = { scrollHeight: 200, scrollTo: vi.fn() }
    setup.query = '测试问题'
    mocks.chatQA.mockImplementationOnce(async (_q, opts) => {
      opts.onChunk({ content: '回答' })
      opts.onEnd({ conversationId: 'conv-2' })
    })
    await setup.send()
    await flush()
    expect(setup.messages.at(-1).content).toBe('回答')
    expect(setup.conversationId).toBe('conv-2')

    setup.autoResize()
    setup.askQuick('快速问题')
    await flush()

    mocks.chatQA.mockRejectedValueOnce(new Error('network'))
    setup.query = '失败问题'
    setup.streaming = false
    await setup.send()
    await flush()
    expect(setup.messages.at(-1).content).toBe('服务暂时繁忙，请稍后重试。')

    setup.query = 'conv-id'
    mocks.chatQA.mockImplementationOnce(async (_q, opts) => {
      opts.onEnd({ conversation_id: 'conv-3' })
    })
    await setup.send()
    await flush()
    expect(setup.conversationId).toBe('conv-3')
  })
})

describe('Consultation/index.vue', () => {
  it('covers toggleVoice and remaining chat branches', async () => {
    const { ElMessage } = await import('element-plus')
    const Consultation = (await import('../Consultation/index.vue')).default
    const wrapper = mountView(Consultation)
    await flush()
    const vm = wrapper.vm

    vm.sending = true
    await vm.toggleVoice()
    vm.sending = false
    vm.transcribing = true
    await vm.toggleVoice()

    vm.transcribing = false
    mocks.startVoice.mockRejectedValueOnce(new Error('denied'))
    await vm.toggleVoice()
    expect(ElMessage.warning).toHaveBeenCalledWith('无法访问麦克风，请检查浏览器权限')

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce({ size: 0 })
    await vm.toggleVoice()

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['v'], { type: 'audio/webm' }))
    mocks.voiceToText.mockResolvedValueOnce({ text: '咨询语音' })
    vm.textareaRef = { style: {}, scrollHeight: 80 }
    await vm.toggleVoice()
    expect(vm.inputMsg).toBe('咨询语音')

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['v'], { type: 'audio/webm' }))
    mocks.voiceToText.mockRejectedValueOnce(new Error('语音错误'))
    await vm.toggleVoice()
    expect(ElMessage.error).toHaveBeenCalledWith('语音错误')

    vm.autoResize()
    vm.aiSuggestion = { possibleDiagnoses: ['A'], treatmentStrategy: '观察' }
    expect(vm.hasAiSuggestion).toBe(true)
    vm.sending = false
    vm.typing = false
    vm.transcribing = false
    mocks.recording.value = false
    vm.messages = [{ sender_type: 'doctor', content: '您好' }]
    expect(vm.showQuickQuestions).toBe(true)
  })
})

describe('CheckinAnalysis/index.vue', () => {
  it('covers chart branches, AI cache and dify refresh', async () => {
    const managementApi = await import('@/api/checkinManagement')
    const CheckinAnalysis = (await import('../CheckinAnalysis/index.vue')).default
    const wrapper = mountView(CheckinAnalysis)
    await flush()
    const vm = wrapper.vm

    vm.chartRef = document.createElement('div')
    vm.trends = {
      glucose: Array.from({ length: 8 }, (_, i) => ({ date: `07-0${i + 1}`, value: 6 + i * 0.1 })),
      food: [{ date: '07-01', count: 1 }],
    }
    vm.chartType = 'glucose'
    vm.renderChart()
    vm.chartType = 'food'
    vm.renderChart()

    vm.aiSummary = '已有'
    managementApi.getAiSummary.mockRejectedValueOnce(new Error('AI fail'))
    await vm.loadAiSummary({ period: 'weekly' }, 'key1')
    expect(mocks.refreshMessages).toHaveBeenCalled()

    managementApi.getAiSummary.mockResolvedValueOnce({ source: 'dify', ai_summary: 'Dify' })
    await vm.loadAiSummary({ period: 'weekly' }, 'key2')
    await vm.handleExport()
  })
})

describe('CheckinRecords', () => {
  it('covers hub empty-state branches', async () => {
    const CheckinRecords = (await import('../CheckinRecords/index.vue')).default
    const wrapper = mountView(CheckinRecords)
    await flush()
    const vm = wrapper.vm

    vm.todayStatus = { today_checkins: [] }
    expect(vm.todayProgress).toBe(0)
    expect(vm.todayTasksSummary).toEqual({ done: 0, total: 4 })
    vm.achievementWall = { achievements: [], total: 0, unlockedCount: 0 }
    expect(vm.achievementPercent).toBe(0)
    expect(vm.isTypeCompleted('food')).toBe(false)
    vm.todayStatus = {
      today_checkins: [{ checkinType: 'diet', completed: true }],
    }
    expect(vm.isTypeCompleted('food')).toBe(true)
  })

  it('covers GlucoseCheckin watch and chart edge cases', async () => {
    const checkinApi = await import('@/api/checkin')
    const echarts = await import('echarts')
    const GlucoseCheckin = (await import('../CheckinRecords/GlucoseCheckin.vue')).default
    const wrapper = mountView(GlucoseCheckin)
    await flush()
    const vm = wrapper.vm

    checkinDate.value = '2026-07-04'
    await flush()
    await flush()
    checkinDate.value = '2026-07-05'
    await flush()
    await flush()
    await vm.loadRecords()
    await vm.loadGlucoseHistory()

    vm.glucoseTrendDays = 7
    await flush()

    vm.glucoseChartRef = document.createElement('div')
    vm.glucoseHistory = { records: [], summary: {} }
    vm.renderGlucoseChart()
    const chart = echarts.init.mock.results.at(-1).value
    const opts = chart.setOption.mock.calls.at(-1)[0]
    expect(opts.yAxis.min).toBe(0)
    expect(opts.yAxis.max).toBe(12)

    vm.glucoseChartRef = document.createElement('div')
    vm.glucoseHistory = {
      records: [{ glucose_value: 5.8, record_time: '2026-07-03 08:00:00', measure_context_label: '空腹' }],
      summary: {},
    }
    vm.renderGlucoseChart()
    vm.submitGlucose()
  })

  it('covers ExerciseCheckin success paths', async () => {
    const checkinApi = await import('@/api/checkin')
    const ExerciseCheckin = (await import('../CheckinRecords/ExerciseCheckin.vue')).default
    const wrapper = mountView(ExerciseCheckin)
    await flush()
    const vm = wrapper.vm

    vm.openExPreset({ exercise_id: 'e1', name: '散步' })
    vm.exDialogDuration = 20
    checkinApi.createExerciseCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await vm.submitExPreset()
    vm.customEx = { name: '跳绳', calories_per_minute: 8, duration: 10 }
    checkinApi.createExerciseCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await vm.submitCustomEx()
  })

  it('covers MedicationCheckin custom preset branch', async () => {
    const checkinApi = await import('@/api/checkin')
    const MedicationCheckin = (await import('../CheckinRecords/MedicationCheckin.vue')).default
    const wrapper = mountView(MedicationCheckin)
    await flush()
    const vm = wrapper.vm

    vm.openMedPreset({ is_user_custom: true, drug_name: '自定义药', image_object_key: 'k1' })
    vm.medDialogDosage = '1片'
    checkinApi.createMedicationCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await vm.submitMedPreset()
    expect(checkinApi.createMedicationCheckin).toHaveBeenCalledWith(expect.objectContaining({
      source_type: 2,
      drug_name: '自定义药',
    }))
  })
})

describe('CheckinReminderSettings/index.vue', () => {
  it('covers load, defaults, slots, notify and save branches', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const userApi = await import('@/api/user')
    const notification = await import('@/utils/notification')
    const CheckinReminderSettings = (await import('../CheckinReminderSettings/index.vue')).default

    reminderApi.getReminderRules.mockResolvedValueOnce([
      { checkin_type: 1, remind_time: '08:00', enabled: true, sort_order: 0 },
      { checkin_type: 99, remind_time: '09:00', enabled: false, sort_order: 0 },
    ])
    const wrapper = mountView(CheckinReminderSettings)
    await flush()

    await wrapper.vm.applyDefaults(true)
    reminderApi.getReminderDefaults.mockRejectedValueOnce(new Error('默认失败'))
    await wrapper.vm.applyDefaults(true)

    wrapper.vm.addSlot(1)
    wrapper.vm.removeSlot(1, 0)
    expect(wrapper.vm.normalizeRemindTime('08:00:00')).toBe('08:00')
    expect(wrapper.vm.normalizeRemindTime('')).toBe('08:00')

    notification.requestNotificationPermission.mockResolvedValueOnce('granted')
    await wrapper.vm.enableBrowserNotify()
    notification.requestNotificationPermission.mockResolvedValueOnce('denied')
    await wrapper.vm.enableBrowserNotify()
    notification.requestNotificationPermission.mockResolvedValueOnce('unsupported')
    await wrapper.vm.enableBrowserNotify()

    reminderApi.saveReminderRules.mockRejectedValueOnce(new Error('保存失败'))
    await wrapper.vm.handleSave()
    await wrapper.vm.handleSave()

    userApi.getUserProfile.mockRejectedValueOnce(new Error('加载失败'))
    await wrapper.vm.loadAll()

    notification.isNotificationSupported.mockReturnValueOnce(false)
    const noNotifyWrapper = mountView(CheckinReminderSettings)
    await flush()
    expect(noNotifyWrapper.vm.notificationSupported).toBe(false)

    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValueOnce('granted')
    const grantedWrapper = mountView(CheckinReminderSettings)
    await flush()
    expect(grantedWrapper.vm.browserNotifyLabel).toBe('已授权')
    expect(grantedWrapper.vm.browserBadgeClass).toBe('is-on')

    notification.getNotificationPermission.mockReturnValueOnce('denied')
    const deniedWrapper = mountView(CheckinReminderSettings)
    await flush()
    expect(deniedWrapper.vm.browserNotifyLabel).toBe('已拒绝')
    expect(deniedWrapper.vm.browserBadgeClass).toBe('is-denied')

    notification.getNotificationPermission.mockReturnValueOnce('default')
    const defaultWrapper = mountView(CheckinReminderSettings)
    await flush()
    expect(defaultWrapper.vm.browserNotifyLabel).toBe('未授权')
    expect(defaultWrapper.vm.browserBadgeClass).toBe('is-off')
  })
})

describe('Auth pages', () => {
  it('covers Login admin branch and error fallback', async () => {
    const auth = await import('@/api/auth.js')
    const Login = (await import('../Login/index.vue')).default
    const wrapper = mountView(Login)
    const vm = wrapper.vm

    vm.formRef = validFormRef()
    vm.form.username = 'admin'
    vm.form.password = '123456'
    auth.login.mockResolvedValueOnce({ role: 'admin' })
    const hrefSpy = vi.spyOn(window.location, 'href', 'set').mockImplementation(() => {})
    await vm.handleSubmit()
    expect(auth.clearTokens).toHaveBeenCalled()
    hrefSpy.mockRestore()

    auth.login.mockRejectedValueOnce({})
    await vm.handleSubmit()
    expect(mocks.error).toHaveBeenCalledWith('登录失败，请检查用户名和密码')
  })

  it('covers Register error fallback', async () => {
    const auth = await import('@/api/auth.js')
    const Register = (await import('../Register/index.vue')).default
    const wrapper = mountView(Register)
    wrapper.vm.formRef = validFormRef()
    wrapper.vm.form.username = 'user'
    wrapper.vm.form.phone = '13800138000'
    wrapper.vm.form.password = '123456'
    auth.register.mockRejectedValueOnce({})
    await wrapper.vm.handleSubmit()
    expect(mocks.error).toHaveBeenCalledWith('注册失败，请稍后重试')
  })

  it('covers ForgotPassword error fallbacks', async () => {
    const auth = await import('@/api/auth.js')
    const ForgotPassword = (await import('../ForgotPassword/index.vue')).default
    const wrapper = mountView(ForgotPassword)
    wrapper.vm.formRef = validFormRef()
    wrapper.vm.form.account = 'test@example.com'
    wrapper.vm.form.code = '123456'
    wrapper.vm.form.newPassword = '123456'
    wrapper.vm.form.confirmPassword = '123456'

    auth.sendVerifyCode.mockRejectedValueOnce({})
    await wrapper.vm.handleSendCode()
    expect(mocks.error).toHaveBeenCalledWith('发送失败，请稍后重试')

    auth.resetPassword.mockRejectedValueOnce({})
    await wrapper.vm.handleSubmit()
    expect(mocks.error).toHaveBeenCalledWith('重置失败，请检查验证码')
  })
})

describe('HealthEvaluation/index.vue', () => {
  it('covers submitAssess pending guard and hidden error branch', async () => {
    const riskApi = await import('@/api/risk')
    const HealthEvaluation = (await import('../HealthEvaluation/index.vue')).default
    const wrapper = mountView(HealthEvaluation)
    await flush()
    const vm = wrapper.vm

    vm.formRef = validFormRef()
    vm.submitting = true
    await vm.submitAssess()
    expect(mocks.info).toHaveBeenCalledWith('分析中，请稍后查看')

    vm.submitting = false
    vm.assessPending = true
    await vm.submitAssess()

    vm.assessPending = false
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    riskApi.assessRisk.mockRejectedValueOnce(new Error('后台失败'))
    await vm.submitAssess()
    await flush()
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
  })

  it('clears pending state when history catches up', async () => {
    const riskApi = await import('@/api/risk')
    const HealthEvaluation = (await import('../HealthEvaluation/index.vue')).default
    const wrapper = mountView(HealthEvaluation)
    await flush()
    const vm = wrapper.vm

    vm.userProfile = { user_id: 'risk-user' }
    vm.assessPending = true
    localStorage.setItem('he_risk_risk-user_pending_at', String(Date.now()))
    riskApi.getRiskHistory.mockResolvedValueOnce({
      list: [{ assessment_id: 'r-new', assessed_at: new Date().toISOString() }],
    })
    await vm.loadHistory()
    expect(vm.assessPending).toBe(false)
  })
})

describe('HealthInfo/index.vue', () => {
  it('covers toggleListen', async () => {
    mocks.route.params = { id: 'a1' }
    const HealthInfo = (await import('../HealthInfo/index.vue')).default
    const wrapper = mountView(HealthInfo)
    await flush()
    wrapper.vm.toggleListen()
    expect(mocks.toggleArticleAudio).toHaveBeenCalledWith('a1')
  })
})

describe('Home/index.vue', () => {
  it('covers empty loops and login-gated navigation', async () => {
    const Home = (await import('../Home/index.vue')).default
    const wrapper = mountView(Home)
    await flush()
    const vm = wrapper.vm

    vm.doctors = []
    vm.articles = []
    expect(vm.loopedDoctors).toEqual([])
    expect(vm.loopedArticles).toEqual([])

    mocks.isLoggedIn.mockReturnValue(false)
    vm.navigateTo('/health-evaluation', true)
    vm.openArticle({ article_id: 'a1' })
    vm.startConsult({ doctor_id: 'd1' })
    vm.openVideo({ id: 'v1', title: '视频' })
    expect(vm.playerVisible).toBe(false)
  })
})

describe('LivingPlans/index.vue', () => {
  it('covers generate stages, favorite guard and mobile history', async () => {
    const planApi = await import('@/api/plan')
    const LivingPlans = (await import('../LivingPlans/index.vue')).default
    const wrapper = mountView(LivingPlans)
    await flush()
    const vm = wrapper.vm

    vm.favoriteLoading = true
    await vm.handleFavorite()
    vm.plan = null
    await vm.handleFavorite()

    planApi.generatePlan.mockImplementationOnce(async ({ onStage }) => {
      onStage({ stage: 'unknown' })
      onStage({ stage: 'complete', plan_id: 'p2' })
    })
    await vm.handleGenerate()

    planApi.getPlanDetail.mockResolvedValueOnce({ plan_id: 'p9', summary: '历史' })
    await vm.loadHistoryPlan('p9')

    mocks.isMobile.value = true
    const rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => { cb(); return 1 })
    vi.spyOn(document, 'getElementById').mockReturnValue({ scrollIntoView: vi.fn() })
    vm.scrollToSection('plan-history')
    expect(vm.historyOpen).toBe(true)
    rafSpy.mockRestore()
    document.getElementById.mockRestore?.()

    vi.spyOn(window, 'print').mockImplementation(() => {})
    vm.handlePrint()
  })
})

describe('UserCenter', () => {
  it('covers stats null branches and format helpers', async () => {
    const userApi = await import('@/api/user')
    const checkinApi = await import('@/api/checkin')
    mocks.route.query = { section: 'checkin-notify' }
    const UserCenter = (await import('../UserCenter/index.vue')).default
    const wrapper = mountView(UserCenter)
    await flush()
    const vm = wrapper.vm

    vm.checkinStats = {}
    expect(vm.quickStats[0].value).toBe('—')
    vm.profile = null
    expect(vm.quickStats[2].value).toBe(0)

    checkinApi.getCheckinStats.mockRejectedValueOnce(new Error('stats fail'))
    userApi.getUserConsultations.mockResolvedValueOnce({ total: 3 })
    await vm.loadStats()

    expect(vm.formatMedicalHistory({ medical_histories: [{ disease_name: '' }] })).toBe('')
  })
})

describe('UserCenter components', () => {
  it('covers ProfileEditDialog avatar and submit branches', async () => {
    const userApi = await import('@/api/user')
    const ProfileEditDialog = (await import('../UserCenter/components/ProfileEditDialog.vue')).default
    const wrapper = mountView(ProfileEditDialog, {
      modelValue: false,
      profile: { nickname: '用户', gender: 'male' },
    })
    await wrapper.setProps({ modelValue: true })
    await flush()
    const vm = wrapper.vm

    vm.fileRef = { click: vi.fn(), value: '' }
    vm.handleAvatarUpload()
    expect(vm.fileRef.click).toHaveBeenCalled()

    await vm.onFileChange({ target: { files: [] } })
    const bigFile = new File(['x'.repeat(3 * 1024 * 1024)], 'big.png', { type: 'image/png' })
    await vm.onFileChange({ target: { files: [bigFile] } })
    expect(mocks.warning).toHaveBeenCalledWith('图片大小不能超过 2MB')

    userApi.uploadUserAvatar.mockResolvedValueOnce({ avatar_url: 'new.png' })
    vm.fileRef = { value: 'x' }
    await vm.onFileChange({ target: { files: [new File(['a'], 'a.png', { type: 'image/png' })] } })
    userApi.uploadUserAvatar.mockRejectedValueOnce(new Error('上传失败'))
    vm.fileRef = { value: 'x' }
    await vm.onFileChange({ target: { files: [new File(['a'], 'a.png', { type: 'image/png' })] } })

    vm.formRef = invalidFormRef()
    await vm.submit()
    vm.formRef = validFormRef()
    await vm.submit()
  })

  it('covers ExportDialog message-only success branch', async () => {
    const userApi = await import('@/api/user')
    const ExportDialog = (await import('../UserCenter/components/ExportDialog.vue')).default
    const wrapper = mountView(ExportDialog, { modelValue: true })
    userApi.exportUserData.mockResolvedValueOnce({ message: '任务已提交' })
    await wrapper.vm.submit()
    expect(mocks.success).toHaveBeenCalledWith('任务已提交')
  })

  it('covers ConsultationDrawer load error', async () => {
    const userApi = await import('@/api/user')
    const ConsultationDrawer = (await import('../UserCenter/components/ConsultationDrawer.vue')).default
    userApi.getUserConsultations.mockRejectedValueOnce(new Error('加载失败'))
    const wrapper = mountView(ConsultationDrawer, { modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await flush()
    expect(wrapper.vm.list).toEqual([])
    expect(mocks.error).toHaveBeenCalledWith('加载失败')
  })

  it('covers HealthRecordDialog and SecurityDialog edge validators', async () => {
    const HealthRecordDialog = (await import('../UserCenter/components/HealthRecordDialog.vue')).default
    const SecurityDialog = (await import('../UserCenter/components/SecurityDialog.vue')).default
    const healthWrapper = mountView(HealthRecordDialog, {
      modelValue: true,
      record: { height: 170, weight: 65, fasting_glucose: 6.1 },
    })
    await flush()
    healthWrapper.vm.formRef = validFormRef()
    await healthWrapper.vm.submit()

    const securityWrapper = mountView(SecurityDialog, {
      modelValue: true,
      profile: { phone: '13800138000', email: 'a@b.com' },
    })
    const cb = vi.fn()
    securityWrapper.vm.emailRules.email[0].validator({}, '', cb)
    expect(cb).toHaveBeenCalled()
  })
})

describe('MessagePopover.vue', () => {
  it('covers formatTime, actionLabel, open and read-all branches', async () => {
    const MessagePopover = (await import('@/components/MessageCenter/MessagePopover.vue')).default
    mocks.messageList.value = [
      {
        message_id: 'm1',
        title: '提醒',
        summary: '摘要',
        created_at: new Date().toISOString(),
        status: 'failed',
      },
      {
        messageId: 'm2',
        title: '咨询',
        message_type: 'consult_reply',
        createdAt: [2026, 7, 1, 8, 0, 0],
        isRead: true,
        link_path: '/consultation/chat',
      },
    ]
    const wrapper = mountView(MessagePopover)
    await flush()
    const setup = setupOf(wrapper)

    expect(setup.formatTime(null)).toBe('')
    expect(setup.formatTime(Date.now() - 30_000)).toBe('刚刚')
    expect(setup.formatTime(Date.now() - 120_000)).toContain('分钟前')
    expect(setup.formatTime(Date.now() - 7_200_000)).toContain('小时前')
    expect(setup.formatTime('2025-01-01T08:00:00')).toContain('2025')
    expect(setup.actionLabel({ status: 'failed' })).toBe('返回重试')
    expect(setup.actionLabel({ message_type: 'consult_reply' })).toBe('进入会话')
    expect(setup.actionLabel({})).toBe('查看')

    mocks.markMessageRead.mockRejectedValueOnce(new Error('ignore'))
    await setup.handleOpen(mocks.messageList.value[0])
    expect(mocks.push).toHaveBeenCalled()

    await setup.handleReadAll()
    expect(mocks.markAllMessagesRead).toHaveBeenCalled()
  })

  it('covers loading and empty states on mount', async () => {
    mocks.messageList.value = []
    mocks.loadList.mockImplementationOnce(async () => { mocks.messageList.value = [] })
    const MessagePopover = (await import('@/components/MessageCenter/MessagePopover.vue')).default
    const wrapper = mountView(MessagePopover)
    await flush()
    expect(wrapper.text()).toContain('暂无消息')
  })

  it('covers parseDateTime invalid array and open without id', async () => {
    const MessagePopover = (await import('@/components/MessageCenter/MessagePopover.vue')).default
    const wrapper = mountView(MessagePopover)
    await flush()
    const setup = setupOf(wrapper)
    expect(setup.formatTime(['bad'])).toBe('')
    await setup.handleOpen({ title: '无ID' })
    expect(mocks.push).toHaveBeenCalled()
  })
})

describe('SiteHeader.vue', () => {
  it('covers mobile back, nav active states and goNav login gate', async () => {
    mocks.isMobile.value = true
    mocks.route.path = '/health-info'
    mocks.isLoggedIn.mockReturnValue(false)
    const SiteHeader = (await import('@/components/layout/SiteHeader.vue')).default
    const wrapper = mountView(SiteHeader, { title: '资讯' })
    await flush()
    const vm = wrapper.vm

    expect(vm.showMobileBack).toBe(true)
    expect(vm.mobileTitle).toBe('资讯')
    expect(vm.navLinks.at(-1).path).toBe('/login')
    expect(vm.isNavActive('/home')).toBe(false)
    expect(vm.isNavActive('/login')).toBe(false)
    mocks.route.path = '/login'
    expect(vm.isNavActive('/login')).toBe(true)
    mocks.route.path = '/health-info/a1'
    expect(vm.isNavActive('/health-info')).toBe(true)

    vm.goNav({ path: '/user-center' })
    expect(mocks.push).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/user-center' } })

    mocks.isLoggedIn.mockReturnValue(true)
    vm.onMessageOpen()
    expect(mocks.loadList).toHaveBeenCalled()
    vm.goBack()
    expect(mocks.safeBack).toHaveBeenCalledWith('/home')

    mocks.route.path = '/home'
    mocks.route.meta = {}
    const homeHeader = mountView(SiteHeader)
    await flush()
    expect(homeHeader.vm.mobileTitle).toBe('糖尿病智能助手')

    mocks.route.path = '/user-center'
    mocks.route.meta = { title: '个人中心' }
    const metaHeader = mountView(SiteHeader)
    await flush()
    expect(metaHeader.vm.mobileTitle).toBe('个人中心')

    mocks.route.path = '/science-videos'
    mocks.route.meta = {}
    const emptyMetaHeader = mountView(SiteHeader)
    await flush()
    expect(emptyMetaHeader.vm.mobileTitle).toBe('')
  })
})
