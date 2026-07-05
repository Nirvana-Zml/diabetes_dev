import { describe, expect, it, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const push = vi.fn()
const route = reactive({
  path: '/home',
  fullPath: '/home',
  name: 'home',
  meta: {},
  params: {},
  query: {},
})

vi.mock('vue-router', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn() }),
  useRoute: () => route,
  createRouter: vi.fn(() => ({
    beforeEach: vi.fn(),
    afterEach: vi.fn(),
    push,
    currentRoute: { value: route },
  })),
  createWebHistory: vi.fn(() => ({})),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve()),
  },
}))

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
  })),
}))

vi.mock('@/api/home', () => ({
  getHomeContent: vi.fn(async () => ({
    banners: [{ id: 'b1', title: 'banner', image: 'banner.png' }],
    videos: [{ id: 'v1', title: '运动科普', cover: 'cover.png', url: 'video.mp4', duration: '3:20' }],
    categories: [{ id: 'diet', name: '饮食' }],
  })),
  getHomeArticles: vi.fn(async () => [{ article_id: 'a1', title: '饮食建议', category: 'diet' }]),
  getDoctors: vi.fn(async () => [{
    doctor_id: 'd1',
    name: '李医生',
    department: '内分泌科',
    title: '主任医师',
    hospital: '市医院',
    status: 'online',
  }]),
  getVideos: vi.fn(async () => ({ list: [{ id: 'v1', title: '运动科普', cover: 'cover.png' }], total: 1 })),
}))

vi.mock('@/api/article', () => ({
  categoryMap: {
    diet: '饮食营养',
    exercise: '运动康复',
    medicine: '用药指导',
  },
  getRecommendArticles: vi.fn(async () => ({ list: [{ article_id: 'a1', title: '推荐文章', category: 'diet' }], total: 1 })),
  getRelatedArticles: vi.fn(async () => ({ list: [{ article_id: 'a2', title: '相关文章' }], total: 1 })),
  reportArticleReadEvent: vi.fn(async () => ({ recorded: true })),
  getArticles: vi.fn(async () => ({ list: [{ article_id: 'a1', title: '文章', summary: '摘要' }], total: 1, page: 1 })),
  getArticleDetail: vi.fn(async () => ({ article_id: 'a1', title: '文章详情', content: '正文', favorited: false })),
  searchArticles: vi.fn(async () => ({ list: [{ article_id: 'a3', title: '搜索文章' }], total: 1 })),
  getArticleFavorites: vi.fn(async () => ({ list: [], total: 0 })),
  toggleArticleFavorite: vi.fn(async () => ({ favorited: true })),
}))

const chatMocks = vi.hoisted(() => ({
  chatQA: vi.fn(),
}))

vi.mock('@/api/chat', () => ({
  chatQA: chatMocks.chatQA,
}))

vi.mock('@/api/checkin', () => ({
  uploadCheckinImage: vi.fn(async () => ({ object_key: 'image-key', url: 'image.png' })),
  getFoodCategories: vi.fn(async () => [{ category_id: 'c1', name: '主食' }]),
  getFoodPresets: vi.fn(async () => [{ food_id: 'f1', name: '米饭', calories: 120 }]),
  createFoodCheckin: vi.fn(async () => ({ points_earned: 10 })),
  getFoodRecords: vi.fn(async () => [{ record_id: 'fr1', name: '米饭' }]),
  getMedicationPresets: vi.fn(async () => [{ medication_id: 'm1', name: '二甲双胍' }]),
  createMedicationCheckin: vi.fn(async () => ({ points_earned: 10 })),
  getMedicationRecords: vi.fn(async () => [{ record_id: 'mr1', name: '二甲双胍' }]),
  getExercisePresets: vi.fn(async () => [{ exercise_id: 'e1', name: '散步' }]),
  createExerciseCheckin: vi.fn(async () => ({ points_earned: 10 })),
  getExerciseRecords: vi.fn(async () => [{ record_id: 'er1', name: '散步' }]),
  createGlucoseCheckin: vi.fn(async () => ({ points_earned: 15 })),
  getGlucoseHistory: vi.fn(async () => ({ list: [{ value: 6.1, measured_at: '2026-06-30' }], total: 1 })),
  getGlucoseRecords: vi.fn(async () => [{ record_id: 'gr1', value: 6.1 }]),
  getTodayStatus: vi.fn(async () => ({ food: true, medication: false, exercise: true, glucose: false })),
  getCheckinStats: vi.fn(async () => ({ total_days: 7, streak_days: 3, points: 100 })),
  getAchievements: vi.fn(async () => [{ id: 'ach1', name: '坚持打卡', unlocked: true }]),
  getAchievementWall: vi.fn(async () => ({
    achievements: [
      { id: 'a1', name: '初来乍到', desc: '完成首次打卡', emoji: '👋', unlocked: true },
      { id: 'a2', name: '坚持不懈', desc: '连续打卡7天', emoji: '🔥', unlocked: false },
    ],
    total: 12,
    unlockedCount: 1,
    progressPercent: 8,
    recentUnlock: { name: '初来乍到', relative: '2天前' },
  })),
}))

vi.mock('@/api/checkinReminder', () => ({
  ackReminder: vi.fn(async () => ({ ok: true })),
  clickReminder: vi.fn(async () => ({ ok: true })),
  getReminderRules: vi.fn(async () => [
    { checkin_type: 1, remind_time: '08:00', enabled: true, sort_order: 0, tab: 'food' },
  ]),
  getReminderDefaults: vi.fn(async () => [
    { checkin_type: 1, remind_time: '08:00', enabled: true, sort_order: 0, tab: 'food' },
  ]),
  saveReminderRules: vi.fn(async (rules) => rules),
}))

vi.mock('@/api/checkinManagement', () => ({
  getManagementStats: vi.fn(async () => ({ total_records: 10, active_days: 5 })),
  getManagementTrends: vi.fn(async () => ({ dates: ['2026-06-30'], glucose: [6.1], food: [1], exercise: [1] })),
  getAiSummary: vi.fn(async () => ({ summary: '趋势稳定', suggestions: ['保持'] })),
  exportReport: vi.fn(async () => ({ export_url: 'report.csv' })),
}))

vi.mock('@/api/consultation', () => ({
  getDepartments: vi.fn(async () => [{ id: 'endo', name: '内分泌科' }]),
  listConsultations: vi.fn(async () => ({ list: [{ session_id: 's1', status: 'open', doctor_name: '李医生' }], total: 1 })),
  createConsultation: vi.fn(async () => ({ session_id: 's1' })),
  getConsultMessages: vi.fn(async () => [{ role: 'doctor', content: '您好' }]),
  sendConsultMessage: vi.fn(async () => ({ message_id: 'msg1', content: '收到' })),
  getAiSuggest: vi.fn(async () => ({ suggestions: ['建议复查'] })),
  closeConsultation: vi.fn(async () => ({ success: true })),
}))

vi.mock('@/api/plan', () => ({
  generatePlan: vi.fn(async ({ onStage } = {}) => {
    onStage?.({ stage: 'calorie', daily_calories: 1800 })
    onStage?.({ stage: 'diet', content: { meal_plan: { breakfast: { foods: ['燕麦'], calories: 300 } } } })
    onStage?.({ stage: 'exercise', content: { weekly_goal: '每周运动', items: [{ name: '散步', intensity: '中等' }] } })
    onStage?.({ stage: 'rest', content: { wake_up: '07:00', sleep: '22:30', glucose_monitor_times: ['早餐前'] } })
    onStage?.({ stage: 'medication', content: '遵医嘱用药' })
    onStage?.({ stage: 'complete', plan_id: 'p1' })
  }),
  getLatestPlan: vi.fn(async () => ({ plan_id: 'p1', diet_plan: {}, exercise_plan: { items: [] }, rest_plan: {} })),
  getPlanDetail: vi.fn(async () => ({ plan_id: 'p1', diet_plan: {}, exercise_plan: { items: [] }, rest_plan: {} })),
  getPlanHistory: vi.fn(async () => ({ list: [{ plan_id: 'p1', version: 1 }], total: 1 })),
  togglePlanFavorite: vi.fn(async () => ({ favorited: true })),
}))

vi.mock('@/api/risk', () => ({
  assessRisk: vi.fn(async () => ({ risk_level: 'low', score: 20, factors: [] })),
  getRiskHistory: vi.fn(async () => ({ list: [{ record_id: 'r1', score: 20 }], total: 1 })),
  getRiskDetail: vi.fn(async () => ({ record_id: 'r1', score: 20, factors: [] })),
}))

vi.mock('@/api/user', () => ({
  getUserProfile: vi.fn(async () => ({ user_id: 'u1', nickname: '测试用户', points: 100, streak_days: 3 })),
  updateUserProfile: vi.fn(async (data) => data),
  uploadUserAvatar: vi.fn(async () => ({ avatar_url: 'avatar.png' })),
  getUserConsultations: vi.fn(async () => ({ list: [{ session_id: 's1' }], total: 1 })),
  getHealthAlert: vi.fn(async () => ({ level: 'normal', message: '正常' })),
  getHealthTrendSummary: vi.fn(async () => ({ summary: '稳定' })),
  acknowledgeHealthIntervention: vi.fn(async () => ({ ok: true })),
  exportUserData: vi.fn(async () => ({ task_id: 't1', status: 'completed', message: '完成' })),
  getExportTask: vi.fn(async () => ({ task_id: 't1', status: 'completed' })),
  updatePrivacySettings: vi.fn(async (data) => data),
  changePassword: vi.fn(async () => ({ success: true })),
  bindEmail: vi.fn(async (data) => data),
  bindPhone: vi.fn(async (data) => data),
  getUserOverview: vi.fn(async () => ({ user_id: 'u1', nickname: '测试用户', points: 100 })),
  getHealthRecord: vi.fn(async () => ({ height: 170, weight: 65, fasting_glucose: 6.1 })),
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
  getHealthRecord: vi.fn(async () => ({ height: 170, weight: 65, fasting_glucose: 6.1 })),
  updateHealthRecord: vi.fn(async (data) => data),
}))

const stubs = [
  'router-link',
  'router-view',
  'el-affix',
  'el-alert',
  'el-avatar',
  'el-badge',
  'el-button',
  'el-card',
  'el-carousel',
  'el-carousel-item',
  'el-checkbox',
  'el-checkbox-group',
  'el-col',
  'el-date-picker',
  'el-descriptions',
  'el-descriptions-item',
  'el-dialog',
  'el-divider',
  'el-drawer',
  'el-empty',
  'el-form',
  'el-form-item',
  'el-icon',
  'el-image',
  'el-input',
  'el-input-number',
  'el-link',
  'el-option',
  'el-pagination',
  'el-popover',
  'el-progress',
  'el-radio',
  'el-radio-button',
  'el-radio-group',
  'el-rate',
  'el-row',
  'el-scrollbar',
  'el-select',
  'el-skeleton',
  'el-skeleton-item',
  'el-slider',
  'el-statistic',
  'el-step',
  'el-steps',
  'el-switch',
  'el-tab-pane',
  'el-table',
  'el-table-column',
  'el-tabs',
  'el-tag',
  'el-time-picker',
  'el-timeline',
  'el-timeline-item',
  'el-tooltip',
  'el-upload',
]

function mountSmoke(component, props = {}) {
  return shallowMount(component, {
    props,
    global: {
      plugins: [createPinia()],
      stubs,
      directives: {
        loading: {},
      },
      mocks: {
        $route: route,
        $router: { push },
      },
    },
  })
}

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function setSetupRef(wrapper, key, value) {
  const setup = wrapper.vm.$?.setupState
  const setupRef = setup?.[key]
  if (setupRef && typeof setupRef === 'object' && 'value' in setupRef) {
    setupRef.value = value
    return
  }
  if (wrapper.vm[key] && typeof wrapper.vm[key] === 'object' && 'value' in wrapper.vm[key]) {
    wrapper.vm[key].value = value
    return
  }
  wrapper.vm[key] = value
}

function dummyPayload() {
  return {
    id: 'a1',
    article_id: 'a1',
    plan_id: 'p1',
    session_id: 's1',
    doctor_id: 'd1',
    category: 'diet',
    name: 'profile',
    label: '测试',
    value: 'test',
    index: 0,
    props: { name: 'profile' },
    row: { id: 'a1', article_id: 'a1', plan_id: 'p1', session_id: 's1' },
    target: {
      value: 'test',
      files: [new File(['avatar'], 'avatar.png', { type: 'image/png' })],
    },
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
  }
}

function methodPayloads(wrapper, key) {
  if (key === 'askQuick' || key === 'useQuickQuestion') {
    return ['饮食建议']
  }
  if (key === 'send' || key === 'sendMsg') {
    try {
      wrapper.vm.query = '饮食建议'
      wrapper.vm.input = '饮食建议'
      wrapper.vm.inputMsg = '饮食建议'
      wrapper.vm.msg = '饮食建议'
    } catch {
      // Best-effort setup for components with readonly public proxies.
    }
    return []
  }
  if (key === 'openVideo') {
    return [{ id: 'v1', title: '运动科普', url: 'video.mp4' }]
  }
  if (key === 'startConsult' || key === 'openChat' || key === 'resolveDoctor') {
    return [{ doctor_id: 'd1', id: 'd1', name: '李医生', status: 'online' }]
  }
  if (key === 'loadHistoryPlan' || key === 'handleFavorite' || key === 'toggleFavOnList' || key === 'goDetail') {
    return ['a1']
  }
  if (key === 'selectCategory' || key === 'switchCategory' || key === 'switchTab') {
    return ['diet']
  }
  if (key === 'changeDate' || key === 'goToStep') {
    return [1]
  }
  if (key === 'onImgError') {
    return [{ target: { src: '' } }]
  }
  if (key === 'handleUpload') {
    return ['food', new File(['avatar'], 'avatar.png', { type: 'image/png' }), { value: '' }]
  }
  if (key === 'onFoodFileChange' || key === 'onMedFileChange') {
    return [{ target: { files: [new File(['avatar'], 'avatar.png', { type: 'image/png' })] } }]
  }
  return [dummyPayload(), dummyPayload()]
}

function methodPayloadVariants(wrapper, key) {
  const base = methodPayloads(wrapper, key)
  const sample = dummyPayload()
  if (['askQuick', 'useQuickQuestion'].includes(key)) {
    return [['饮食建议'], ['']]
  }
  if (['send', 'sendMsg'].includes(key)) {
    return [base, []]
  }
  return [
    base,
    [],
    [undefined],
    [null],
    [''],
    ['unknown'],
    ['active'],
    ['closed'],
    ['low'],
    ['medium'],
    ['high'],
    [0],
    [1],
    [{ ...sample, status: 'active', value: 3.8, score: 15, level: 'low' }],
    [{ ...sample, status: 'closed', value: 6.8, score: 55, level: 'medium' }],
    [{ ...sample, status: 'cancelled', value: 12.1, score: 90, level: 'high' }],
    [{ target: { value: '', files: [] } }],
    [{ target: { value: '测试输入', files: [new File(['avatar'], 'avatar.png', { type: 'image/png' })] } }],
  ]
}

function assignVm(wrapper, values) {
  for (const [key, value] of Object.entries(values)) {
    try {
      wrapper.vm[key] = value
    } catch {
      // Some public proxy fields are readonly computed values.
    }
  }
}

async function exerciseStateVariants(wrapper) {
  const sample = dummyPayload()
  const states = [
    {
      loading: false,
      list: [],
      records: [],
      articles: [],
      messages: [],
      history: [],
      currentTab: 'all',
      activeTab: 'all',
      activeName: 'all',
      dialogVisible: false,
      visible: false,
      modelValue: false,
      query: '',
      keyword: '',
      searchKeyword: '',
    },
    {
      loading: true,
      list: [sample],
      records: [sample],
      articles: [sample],
      messages: [
        { role: 'user', content: '用户消息', created_at: '2026-07-01 08:00:00' },
        { role: 'doctor', content: '医生回复', created_at: '2026-07-01 08:01:00' },
      ],
      history: [sample],
      currentTab: 'diet',
      activeTab: 'profile',
      activeName: 'profile',
      dialogVisible: true,
      visible: true,
      modelValue: true,
      query: '饮食',
      keyword: '饮食',
      searchKeyword: '饮食',
    },
    {
      loading: false,
      status: 'active',
      selectedDate: '2026-07-01',
      selectedCategory: 'exercise',
      currentStep: 0,
      currentPage: 1,
      page: 1,
      total: 1,
      hasMore: false,
      isFavorite: true,
      favorited: true,
      form: { ...sample, nickname: '测试用户', gender: 'male', height: 170, weight: 65 },
      profile: { ...sample, nickname: '测试用户', gender: 'female' },
    },
    {
      loading: false,
      status: 'closed',
      selectedDate: '',
      selectedCategory: 'unknown',
      currentStep: 99,
      currentPage: 2,
      page: 2,
      total: 0,
      hasMore: true,
      isFavorite: false,
      favorited: false,
      form: { ...sample, nickname: '', gender: '', height: null, weight: null },
      profile: {},
    },
  ]

  for (const state of states) {
    assignVm(wrapper, state)
    await flush()
  }
}

async function exercisePublicMethods(wrapper) {
  const methodNames = [
    'addFamilyHistory',
    'addMedicalHistory',
    'addMedication',
    'anomalyLabel',
    'applyAiData',
    'askQuick',
    'assessRisk',
    'autoResize',
    'bmiLevelText',
    'calcExCalories',
    'calcFoodCalories',
    'calcGrams',
    'calcPresetFoodCalories',
    'categoryBadgeStyle',
    'changeDate',
    'clearSearch',
    'closeConsultation',
    'confidenceText',
    'consultBtnClass',
    'consultBtnText',
    'exportReport',
    'factorLevelClass',
    'flushReadEvent',
    'formatChartLabel',
    'formatDate',
    'formatDetailDate',
    'formatFoodAmount',
    'formatMedicalHistory',
    'formatMedication',
    'formatRelative',
    'formatTime',
    'formatViewCount',
    'genderLabel',
    'giLabel',
    'giTagType',
    'glucoseStatusLabel',
    'glucoseText',
    'go',
    'goBack',
    'goDetail',
    'goHome',
    'goList',
    'goLogin',
    'goNav',
    'goToStep',
    'handleAvatarUpload',
    'handleExport',
    'handleFavorite',
    'handleGenerate',
    'handleLogout',
    'handleMenu',
    'handlePrint',
    'handleSearch',
    'handleSearchClear',
    'handleSendCode',
    'handleShare',
    'handleSubmit',
    'handleUpload',
    'initChatFromRoute',
    'intensityType',
    'isActive',
    'isFavoriteFlag',
    'isFavorited',
    'isNavActive',
    'isThinking',
    'levelText',
    'loadAiSummary',
    'loadArticles',
    'loadData',
    'loadDetail',
    'loadDoctors',
    'loadFavoriteIds',
    'loadGlucoseHistory',
    'loadHealthRecord',
    'loadHeroData',
    'loadHistory',
    'loadHistoryPlan',
    'loadPage',
    'loadPlan',
    'loadPresets',
    'loadProfile',
    'loadRecords',
    'loadTabData',
    'loadVideos',
    'navigateTo',
    'onFoodFileChange',
    'onHealthSaved',
    'onImgError',
    'onMedFileChange',
    'onProfileSaved',
    'openChat',
    'openExPreset',
    'openFoodPreset',
    'openMedPreset',
    'openVideo',
    'patternTagType',
    'probText',
    'readAiCache',
    'renderChart',
    'renderCharts',
    'renderGlucoseChart',
    'resetQuestionnaire',
    'resolveDoctor',
    'restoreAiCache',
    'runSearch',
    'savePrivacy',
    'scrollToBottom',
    'selectCategory',
    'send',
    'sendMsg',
    'showDateDivider',
    'startConsult',
    'startSession',
    'statusText',
    'statusType',
    'submit',
    'submitAssess',
    'submitClose',
    'submitCustomEx',
    'submitCustomFood',
    'submitCustomMed',
    'submitExPreset',
    'submitFoodPreset',
    'submitGlucose',
    'submitMedPreset',
    'switchCategory',
    'switchTab',
    'syncFavoriteInHistory',
    'tagStyle',
    'toggleFav',
    'toggleFavOnList',
    'truncateTitle',
    'triggerFoodUpload',
    'triggerMedUpload',
    'typeLabel',
    'updateBannerHeight',
    'useQuickQuestion',
    'viewHistory',
    'writeAiCache',
  ]
  const seen = new Set()
  for (const key of methodNames) {
    const value = wrapper.vm[key] ?? wrapper.vm.$?.setupState?.[key]
    if (typeof value !== 'function') {
      continue
    }
    seen.add(key)

    for (const args of methodPayloadVariants(wrapper, key)) {
      try {
        await value(...args)
      } catch {
        // Smoke coverage deliberately probes handlers with generic payloads.
      }
      await flush()
    }
  }
}

async function exerciseRenderedEvents(wrapper) {
  const selectors = [
    'button',
    'a',
    'input',
    'textarea',
    'form',
    'el-button-stub',
    'el-card-stub',
    'el-link-stub',
    'el-tag-stub',
    'el-tab-pane-stub',
    'el-tabs-stub',
    'el-upload-stub',
    'el-switch-stub',
    'el-radio-button-stub',
    'el-pagination-stub',
  ]
  const events = ['click', 'change', 'input', 'submit']

  for (const selector of selectors) {
    for (const node of wrapper.findAll(selector)) {
      for (const event of events) {
        try {
          await node.trigger(event, dummyPayload())
        } catch {
          // Template event probing is best-effort for coverage only.
        }
      }
    }
  }
  await flush()
}

async function exerciseStubComponentEvents(wrapper) {
  const componentNames = [
    'ElButton',
    'ElCard',
    'ElLink',
    'ElTag',
    'ElTabs',
    'ElTabPane',
    'ElUpload',
    'ElSwitch',
    'ElRadioButton',
    'ElPagination',
    'ElDatePicker',
    'ElSelect',
    'ElInput',
    'ElInputNumber',
    'ElDialog',
    'ElDrawer',
  ]
  const events = [
    'click',
    'change',
    'input',
    'submit',
    'tab-click',
    'current-change',
    'size-change',
    'update:modelValue',
    'close',
    'success',
    'error',
  ]

  for (const name of componentNames) {
    for (const component of wrapper.findAllComponents({ name })) {
      for (const event of events) {
        try {
          component.vm.$emit(event, dummyPayload())
        } catch {
          // Component event probing is best-effort for coverage only.
        }
        await flush()
      }
    }
  }
}

async function exerciseComponent(wrapper) {
  await exerciseStateVariants(wrapper)
  await exercisePublicMethods(wrapper)
  await exerciseRenderedEvents(wrapper)
  await exerciseStubComponentEvents(wrapper)
  await exerciseStateVariants(wrapper)
}

beforeEach(() => {
  vi.clearAllMocks()
  chatMocks.chatQA.mockImplementation(async (_query, options = {}) => {
    options.onChunk?.({ content: '你好' })
    options.onEnd?.({ answer: '你好', conversation_id: 'conv-1' })
    return { answer: '你好' }
  })
  push.mockClear()
  Object.assign(route, {
    path: '/home',
    fullPath: '/home',
    name: 'home',
    meta: {},
    params: {},
    query: {},
  })
  setActivePinia(createPinia())
  localStorage.clear()
})

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

describe('frontend views smoke coverage', () => {
  it('mounts public auth pages', async () => {
    const Login = (await import('../Login/index.vue')).default
    const Register = (await import('../Register/index.vue')).default
    const ForgotPassword = (await import('../ForgotPassword/index.vue')).default

    localStorage.setItem('remember_username', 'saved_user')
    for (const component of [Login, Register, ForgotPassword]) {
      const wrapper = mountSmoke(component)
      expect(wrapper.exists()).toBe(true)
      await exerciseComponent(wrapper)
      wrapper.unmount()
    }
    await flush()
  }, 15000)

  it('mounts main feature pages', async () => {
    const modules = await Promise.all([
      import('../Home/index.vue'),
      import('../Assistant/index.vue'),
      import('../ScienceVideos/index.vue'),
      import('../HealthInfo/index.vue'),
      import('../Consultation/index.vue'),
      import('../LivingPlans/index.vue'),
      import('../HealthEvaluation/index.vue'),
      import('../CheckinAnalysis/index.vue'),
      import('../CheckinRecords/index.vue'),
      import('../CheckinRecords/FoodCheckin.vue'),
      import('../CheckinRecords/MedicationCheckin.vue'),
      import('../CheckinRecords/ExerciseCheckin.vue'),
      import('../CheckinRecords/GlucoseCheckin.vue'),
      import('../CheckinRecords/AchievementWall.vue'),
      import('../CheckinReminderSettings/index.vue'),
      import('../UserCenter/index.vue'),
    ])

    for (const mod of modules) {
      const wrapper = mountSmoke(mod.default)
      expect(wrapper.exists()).toBe(true)
      await exerciseComponent(wrapper)
      await flush()
      wrapper.unmount()
    }
  }, 20000)

  it('mounts user center dialogs and layout components', async () => {
    const modules = await Promise.all([
      import('../UserCenter/components/ConsultationDrawer.vue'),
      import('../UserCenter/components/ExportDialog.vue'),
      import('../UserCenter/components/HealthRecordDialog.vue'),
      import('../UserCenter/components/ProfileEditDialog.vue'),
      import('../UserCenter/components/SecurityDialog.vue'),
      import('@/components/AiChatDialog.vue'),
      import('@/components/VideoPlayerDialog.vue'),
      import('@/components/layout/AppLayout.vue'),
      import('@/components/layout/BottomNav.vue'),
      import('@/components/layout/SiteFooter.vue'),
      import('@/components/layout/SiteHeader.vue'),
      import('@/components/layout/SiteLayout.vue'),
      import('@/components/layout/TopNav.vue'),
      import('@/App.vue'),
    ])

    for (const mod of modules) {
      const wrapper = mountSmoke(mod.default, { modelValue: true })
      expect(wrapper.exists()).toBe(true)
      await exerciseComponent(wrapper)
      await flush()
      wrapper.unmount()
    }
  })

  it('covers auth page validation, success and failure branches', async () => {
    const auth = await import('@/api/auth.js')
    const Login = (await import('../Login/index.vue')).default
    const Register = (await import('../Register/index.vue')).default
    const ForgotPassword = (await import('../ForgotPassword/index.vue')).default

    const loginWrapper = mountSmoke(Login)
    loginWrapper.vm.formRef = invalidFormRef()
    await loginWrapper.vm.handleSubmit()
    expect(auth.login).not.toHaveBeenCalled()

    loginWrapper.vm.formRef = validFormRef()
    loginWrapper.vm.form.username = ' admin '
    loginWrapper.vm.form.password = '123456'
    auth.login.mockResolvedValueOnce({ role: 'admin' })
    await loginWrapper.vm.handleSubmit()
    expect(auth.clearTokens).toHaveBeenCalled()

    loginWrapper.vm.form.username = ' user '
    loginWrapper.vm.form.remember = true
    auth.login.mockResolvedValueOnce({ user: { role: 'user' }, access_token: 't' })
    route.query = { redirect: '/user' }
    await loginWrapper.vm.handleSubmit()
    expect(auth.saveTokens).toHaveBeenCalled()
    expect(localStorage.getItem('remember_username')).toBe('user')

    loginWrapper.vm.form.remember = false
    auth.login.mockResolvedValueOnce({ role: 'user', access_token: 't2' })
    await loginWrapper.vm.handleSubmit()
    expect(localStorage.getItem('remember_username')).toBeNull()

    auth.login.mockRejectedValueOnce(new Error('登录异常'))
    await loginWrapper.vm.handleSubmit()

    const registerWrapper = mountSmoke(Register)
    registerWrapper.vm.formRef = invalidFormRef()
    await registerWrapper.vm.handleSubmit()
    expect(auth.register).not.toHaveBeenCalled()

    registerWrapper.vm.formRef = validFormRef()
    registerWrapper.vm.form.username = ' new_user '
    registerWrapper.vm.form.phone = '13800138000'
    registerWrapper.vm.form.password = '123456'
    auth.register.mockResolvedValueOnce({ user_id: 'u2' })
    await registerWrapper.vm.handleSubmit()
    expect(auth.register).toHaveBeenCalledWith({
      username: 'new_user',
      phone: '13800138000',
      password: '123456',
    })

    auth.register.mockRejectedValueOnce(new Error('注册异常'))
    await registerWrapper.vm.handleSubmit()
    const confirmRule = registerWrapper.vm.rules.confirmPassword[1].validator
    const registerCallbacks = [vi.fn(), vi.fn()]
    confirmRule({}, 'bad', registerCallbacks[0])
    confirmRule({}, registerWrapper.vm.form.password, registerCallbacks[1])
    expect(registerCallbacks[0].mock.calls[0][0]).toBeInstanceOf(Error)
    expect(registerCallbacks[1]).toHaveBeenCalledWith()

    const forgotWrapper = mountSmoke(ForgotPassword)
    forgotWrapper.vm.formRef = invalidFormRef()
    await forgotWrapper.vm.handleSendCode()
    await forgotWrapper.vm.handleSubmit()

    forgotWrapper.vm.formRef = validFormRef()
    forgotWrapper.vm.form.account = 'test@example.com'
    forgotWrapper.vm.form.code = '123456'
    forgotWrapper.vm.form.newPassword = '123456'
    forgotWrapper.vm.form.confirmPassword = '123456'
    vi.useFakeTimers()
    auth.sendVerifyCode.mockResolvedValueOnce({ success: true })
    await forgotWrapper.vm.handleSendCode()
    vi.advanceTimersByTime(60_000)
    expect(forgotWrapper.vm.countdown).toBe(0)
    vi.useRealTimers()

    auth.sendVerifyCode.mockRejectedValueOnce(new Error('发送异常'))
    await forgotWrapper.vm.handleSendCode()

    auth.resetPassword.mockResolvedValueOnce({ success: true })
    await forgotWrapper.vm.handleSubmit()

    auth.resetPassword.mockRejectedValueOnce(new Error('重置异常'))
    await forgotWrapper.vm.handleSubmit()

    forgotWrapper.vm.form.account = ''
    expect(forgotWrapper.vm.form.account).toBe('')

    const accountRule = forgotWrapper.vm.rules.account[0].validator
    const confirmForgotRule = forgotWrapper.vm.rules.confirmPassword[1].validator
    for (const value of ['', 'bad', 'ok@example.com']) {
      const cb = vi.fn()
      accountRule({}, value, cb)
      expect(cb).toHaveBeenCalled()
    }
    const forgotCallbacks = [vi.fn(), vi.fn()]
    confirmForgotRule({}, 'bad', forgotCallbacks[0])
    confirmForgotRule({}, forgotWrapper.vm.form.newPassword, forgotCallbacks[1])
    expect(forgotCallbacks[0].mock.calls[0][0]).toBeInstanceOf(Error)
    expect(forgotCallbacks[1]).toHaveBeenCalledWith()
  })

  it('covers user center dialog submit and validation branches', async () => {
    const userApi = await import('@/api/user')
    const auth = await import('@/api/auth.js')
    const elementPlus = await import('element-plus')
    const ExportDialog = (await import('../UserCenter/components/ExportDialog.vue')).default
    const HealthRecordDialog = (await import('../UserCenter/components/HealthRecordDialog.vue')).default
    const SecurityDialog = (await import('../UserCenter/components/SecurityDialog.vue')).default
    const ConsultationDrawer = (await import('../UserCenter/components/ConsultationDrawer.vue')).default

    const exportWrapper = mountSmoke(ExportDialog, { modelValue: true })
    exportWrapper.vm.types = []
    await exportWrapper.vm.submit()
    userApi.exportUserData.mockResolvedValueOnce({ download_url: 'report.pdf' })
    exportWrapper.vm.types = ['health', 'risk']
    exportWrapper.vm.format = 'excel'
    exportWrapper.vm.dateRange = ['2026-01-01', '2026-01-31']
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    await exportWrapper.vm.submit()
    expect(openSpy).toHaveBeenCalledWith('report.pdf', '_blank')
    userApi.exportUserData.mockRejectedValueOnce(new Error('导出异常'))
    await exportWrapper.vm.submit()
    openSpy.mockRestore()

    const healthWrapper = mountSmoke(HealthRecordDialog, {
      modelValue: false,
      record: { height: 170, weight: 65, fasting_glucose: 6.1, systolic_bp: 120, diastolic_bp: 80 },
    })
    await healthWrapper.setProps({ modelValue: true })
    await flush()
    healthWrapper.vm.formRef = invalidFormRef()
    await healthWrapper.vm.submit()
    healthWrapper.vm.formRef = validFormRef()
    healthWrapper.vm.form = { fasting_glucose: 31 }
    await healthWrapper.vm.submit()
    healthWrapper.vm.form = { fasting_glucose: 16, height: 170, weight: 65 }
    elementPlus.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    await healthWrapper.vm.submit()
    healthWrapper.vm.form = { fasting_glucose: 16, height: 170, weight: 65 }
    elementPlus.ElMessageBox.confirm.mockResolvedValueOnce()
    userApi.updateHealthRecord.mockResolvedValueOnce({ saved: true })
    await healthWrapper.vm.submit()

    const securityWrapper = mountSmoke(SecurityDialog, {
      modelValue: true,
      profile: { phone: '', email: '' },
    })
    const emailValidator = securityWrapper.vm.emailRules.email[0].validator
    const passwordValidator = securityWrapper.vm.pwdRules.confirm_password[1].validator
    for (const value of ['', 'bad', 'ok@example.com']) {
      const cb = vi.fn()
      emailValidator({}, value, cb)
      expect(cb).toHaveBeenCalled()
    }
    const pwdCallbacks = [vi.fn(), vi.fn()]
    securityWrapper.vm.pwdForm.new_password = 'newpass'
    passwordValidator({}, 'bad', pwdCallbacks[0])
    passwordValidator({}, 'newpass', pwdCallbacks[1])
    expect(pwdCallbacks[0].mock.calls[0][0]).toBeInstanceOf(Error)
    expect(pwdCallbacks[1]).toHaveBeenCalledWith()

    securityWrapper.vm.pwdRef = invalidFormRef()
    await securityWrapper.vm.submitPassword()
    securityWrapper.vm.pwdRef = validFormRef()
    securityWrapper.vm.pwdForm.old_password = 'oldpass'
    securityWrapper.vm.pwdForm.new_password = 'newpass'
    securityWrapper.vm.pwdForm.confirm_password = 'newpass'
    userApi.changePassword.mockResolvedValueOnce({ success: true })
    await securityWrapper.vm.submitPassword()

    securityWrapper.vm.phoneForm.phone = '123'
    await securityWrapper.vm.sendPhoneCode()
    await securityWrapper.vm.bindPhoneSubmit()
    securityWrapper.vm.phoneForm.phone = '13800138000'
    securityWrapper.vm.phoneForm.code = '123'
    await securityWrapper.vm.bindPhoneSubmit()
    securityWrapper.vm.phoneForm.code = '123456'
    vi.useFakeTimers()
    auth.sendVerifyCode.mockResolvedValueOnce({ success: true })
    await securityWrapper.vm.sendPhoneCode()
    vi.advanceTimersByTime(60_000)
    expect(securityWrapper.vm.phoneCodeCooldown).toBe(0)
    vi.useRealTimers()
    auth.sendVerifyCode.mockRejectedValueOnce(new Error('发送失败'))
    await securityWrapper.vm.sendPhoneCode()
    userApi.bindPhone.mockResolvedValueOnce({ phone: '13800138000' })
    await securityWrapper.vm.bindPhoneSubmit()
    userApi.bindPhone.mockRejectedValueOnce(new Error('绑定失败'))
    await securityWrapper.vm.bindPhoneSubmit()

    securityWrapper.vm.emailRef = invalidFormRef()
    await securityWrapper.vm.sendEmailCode()
    await securityWrapper.vm.bindEmailSubmit()
    securityWrapper.vm.emailRef = validFormRef()
    securityWrapper.vm.emailForm.email = 'test@example.com'
    securityWrapper.vm.emailForm.code = '123456'
    vi.useFakeTimers()
    auth.sendVerifyCode.mockResolvedValueOnce({ success: true })
    await securityWrapper.vm.sendEmailCode()
    vi.advanceTimersByTime(60_000)
    expect(securityWrapper.vm.emailCodeCooldown).toBe(0)
    vi.useRealTimers()
    auth.sendVerifyCode.mockRejectedValueOnce(new Error('发送失败'))
    await securityWrapper.vm.sendEmailCode()
    userApi.bindEmail.mockResolvedValueOnce({ email: 'test@example.com' })
    await securityWrapper.vm.bindEmailSubmit()
    userApi.bindEmail.mockRejectedValueOnce(new Error('绑定失败'))
    await securityWrapper.vm.bindEmailSubmit()

    userApi.getUserConsultations.mockRejectedValueOnce(new Error('加载失败'))
    const drawerWrapper = mountSmoke(ConsultationDrawer, { modelValue: false })
    await drawerWrapper.setProps({ modelValue: true })
    await flush()
    userApi.getUserConsultations.mockResolvedValueOnce({ list: [{ session_id: 's2', status: 'active' }] })
    await drawerWrapper.setProps({ modelValue: false })
    await drawerWrapper.setProps({ modelValue: true })
    await flush()
  })

  it('covers main view branch helpers and async fallbacks', async () => {
    const articleApi = await import('@/api/article')
    const homeApi = await import('@/api/home')
    const managementApi = await import('@/api/checkinManagement')
    const consultationApi = await import('@/api/consultation')
    const planApi = await import('@/api/plan')
    const userApi = await import('@/api/user')

    const Assistant = (await import('../Assistant/index.vue')).default
    const CheckinAnalysis = (await import('../CheckinAnalysis/index.vue')).default
    const Consultation = (await import('../Consultation/index.vue')).default
    const HealthInfo = (await import('../HealthInfo/index.vue')).default
    const Home = (await import('../Home/index.vue')).default
    const LivingPlans = (await import('../LivingPlans/index.vue')).default
    const UserCenter = (await import('../UserCenter/index.vue')).default

    const homeWrapper = mountSmoke(Home)
    await flush()
    homeWrapper.vm.updateBannerHeight()
    expect(homeWrapper.vm.bannerHeight).toContain('px')
    expect(homeWrapper.vm.quickEntries).toHaveLength(4)
    expect(homeWrapper.vm.quickEntries.at(-1).path).toBe('/checkin-reminder-settings')
    homeWrapper.vm.$.setupState.userStore.profile = { privacy_settings: { checkin_notify: false } }
    expect(homeWrapper.vm.quickEntries.at(-1).path).toEqual({ path: '/user-center', query: { section: 'checkin-notify' } })
    expect(homeWrapper.vm.loopedDoctors.length).toBeGreaterThanOrEqual(homeWrapper.vm.doctors.length)
    expect(homeWrapper.vm.loopedArticles.length).toBeGreaterThanOrEqual(homeWrapper.vm.articles.length)
    expect(homeWrapper.vm.consultBtnText('online')).toBe('立即咨询')
    expect(homeWrapper.vm.consultBtnText('offline')).toBe('离线留言')
    expect(homeWrapper.vm.consultBtnText('busy')).toBe('排队中')
    expect(homeWrapper.vm.consultBtnText('other')).toBe('立即咨询')
    expect(homeWrapper.vm.consultBtnClass('online')).toBe('consult-btn--primary')
    expect(homeWrapper.vm.consultBtnClass('offline')).toBe('consult-btn--muted')
    expect(homeWrapper.vm.consultBtnClass('busy')).toBe('consult-btn--busy')
    expect(homeWrapper.vm.consultBtnClass('other')).toBe('consult-btn--primary')
    expect(homeWrapper.vm.formatRelative(new Date().toISOString())).toBeTruthy()
    homeWrapper.vm.navigateTo('/health-evaluation', true)
    expect(push).not.toHaveBeenLastCalledWith('/health-evaluation')
    localStorage.setItem('access_token', 'token')
    homeWrapper.vm.navigateTo('/health-evaluation', true)
    expect(push).toHaveBeenLastCalledWith('/health-evaluation')
    homeWrapper.vm.openArticle({ article_id: 'a100' })
    expect(push).toHaveBeenLastCalledWith('/health-info/a100')
    homeWrapper.vm.startConsult({ doctor_id: 'd100' })
    expect(push).toHaveBeenLastCalledWith({ path: '/consultation/chat', query: { doctor_id: 'd100' } })
    homeWrapper.vm.openVideo({ id: 'v100', title: '视频' })
    expect(homeWrapper.vm.playerVisible).toBe(true)
    expect(homeWrapper.vm.playingVideo.title).toBe('视频')
    homeWrapper.unmount()

    homeApi.getHomeContent.mockRejectedValueOnce(new Error('content failed'))
    homeApi.getHomeArticles.mockRejectedValueOnce(new Error('articles failed'))
    homeApi.getDoctors.mockRejectedValueOnce(new Error('doctors failed'))
    const rejectedHomeWrapper = mountSmoke(Home)
    await flush()
    expect(rejectedHomeWrapper.vm.banners).toEqual([])
    expect(rejectedHomeWrapper.vm.articles).toEqual([])
    expect(rejectedHomeWrapper.vm.doctors).toEqual([])
    rejectedHomeWrapper.unmount()

    const assistantWrapper = mountSmoke(Assistant)
    assistantWrapper.vm.textareaRef = { style: {}, scrollHeight: 200 }
    assistantWrapper.vm.msgRef = { scrollHeight: 100, scrollTo: vi.fn() }
    assistantWrapper.vm.autoResize()
    assistantWrapper.vm.query = '异常问题'
    chatMocks.chatQA.mockRejectedValueOnce(new Error('ai busy'))
    await assistantWrapper.vm.send()
    await flush()
    expect(assistantWrapper.vm.messages.at(-1).content).toBe('服务暂时繁忙，请稍后重试。')

    const analysisWrapper = mountSmoke(CheckinAnalysis)
    analysisWrapper.vm.chartRef = document.createElement('div')
    analysisWrapper.vm.trends = {
      glucose: [{ date: '2026-07-01', value: 6.1 }],
      food: [{ date: '2026-07-01', count: 1 }],
    }
    analysisWrapper.vm.chartType = 'food'
    analysisWrapper.vm.renderChart()
    sessionStorage.setItem('checkin_ai_summary_cache', '{bad json')
    expect(analysisWrapper.vm.readAiCache()).toBeNull()
    analysisWrapper.vm.writeAiCache('k1', {
      ai_summary: '稳定',
      behavior_patterns: [{ type: '规律' }],
      anomalies: [{ type: 'glucose_abnormal' }],
      improvements: ['保持'],
    })
    const setItemSpy = vi.spyOn(window.sessionStorage, 'setItem').mockImplementationOnce(() => {
      throw new Error('quota')
    })
    analysisWrapper.vm.writeAiCache('quota', { ai_summary: '缓存失败' })
    setItemSpy.mockRestore()
    expect(analysisWrapper.vm.restoreAiCache('k2')).toBe(true)
    expect(analysisWrapper.vm.typeLabel('diet')).toBe('饮食')
    expect(analysisWrapper.vm.typeLabel('other')).toBe('other')
    expect(analysisWrapper.vm.anomalyLabel('missed_all')).toBe('全部漏打卡')
    expect(analysisWrapper.vm.patternTagType('规律')).toBe('success')
    expect(analysisWrapper.vm.patternTagType('不稳定')).toBe('warning')
    analysisWrapper.vm.aiSummary = ''
    analysisWrapper.vm.behaviorPatterns = []
    analysisWrapper.vm.anomalies = []
    analysisWrapper.vm.improvements = []
    expect(analysisWrapper.vm.hasAiContent).toBe(false)
    expect(analysisWrapper.vm.aiRefreshTip).toBe('正在生成 AI 行为分析，请稍候…')
    analysisWrapper.vm.aiSummary = '已有分析'
    analysisWrapper.vm.aiCacheStale = false
    expect(analysisWrapper.vm.aiRefreshTip).toContain('最近一次')
    analysisWrapper.vm.aiCacheStale = true
    expect(analysisWrapper.vm.aiRefreshTip).toContain('上次查看')
    analysisWrapper.vm.setPeriod('monthly')
    expect(analysisWrapper.vm.period).toBe('monthly')
    await flush()
    managementApi.getManagementStats.mockRejectedValueOnce(new Error('统计异常'))
    await analysisWrapper.vm.loadData()
    analysisWrapper.vm.aiSummary = ''
    analysisWrapper.vm.behaviorPatterns = []
    analysisWrapper.vm.anomalies = []
    analysisWrapper.vm.improvements = []
    managementApi.getAiSummary.mockRejectedValueOnce(new Error('AI 异常'))
    await analysisWrapper.vm.loadAiSummary({ period: 'weekly' }, 'k3')
    managementApi.getAiSummary.mockResolvedValueOnce({ source: 'dify', ai_summary: 'Dify 分析' })
    await analysisWrapper.vm.loadAiSummary({ period: 'monthly' }, 'k4')
    await analysisWrapper.vm.handleExport()

    const planWrapper = mountSmoke(LivingPlans)
    expect(planWrapper.vm.formatTime()).toBe('')
    const dateLocaleSpy = vi.spyOn(Date.prototype, 'toLocaleString').mockImplementationOnce(() => {
      throw new Error('bad date format')
    })
    expect(planWrapper.vm.formatTime('bad-date')).toBe('bad-date')
    dateLocaleSpy.mockRestore()
    expect(planWrapper.vm.giLabel('low')).toBe('低')
    expect(planWrapper.vm.giLabel()).toBe('—')
    expect(planWrapper.vm.giTagType('high')).toBe('danger')
    expect(planWrapper.vm.giTagType('other')).toBe('info')
    expect(planWrapper.vm.intensityType()).toBe('info')
    expect(planWrapper.vm.intensityType('高强度')).toBe('danger')
    expect(planWrapper.vm.intensityType('中等')).toBe('warning')
    expect(planWrapper.vm.intensityType('轻松')).toBe('success')
    planWrapper.vm.generating = true
    planWrapper.vm.plan = null
    planWrapper.vm.previewPlan = null
    expect(planWrapper.vm.dimensions.map((d) => d.percent)).toEqual([40, 60, 80, 0])
    planWrapper.vm.previewPlan = {
      medication_note: '遵医嘱',
      diet_plan: {
        meal_plan: {
          breakfast: { time: '08:00', foods: ['燕麦'], total_calories: 320 },
          custom: { foods: ['坚果'], calories: 90 },
        },
        diet_principles: ['少糖'],
        foods_to_recommend: ['蔬菜'],
        foods_to_avoid: ['糖'],
      },
      exercise_plan: [{ name: '散步', intensity: '中等' }],
      rest_plan: { wake_up: '07:00', sleep: '22:30', glucose_monitor_times: ['早餐前'], routine_tips: ['早睡'] },
    }
    expect(planWrapper.vm.mealEntries).toHaveLength(2)
    expect(planWrapper.vm.dietPrinciples).toEqual(['少糖'])
    expect(planWrapper.vm.foodsRecommend).toEqual(['蔬菜'])
    expect(planWrapper.vm.foodsAvoid).toEqual(['糖'])
    expect(planWrapper.vm.exerciseItems).toHaveLength(1)
    expect(planWrapper.vm.hasRestPlan).toBe(true)
    expect(planWrapper.vm.restTips).toEqual(['早睡'])
    expect(planWrapper.vm.readyDimCount).toBe(4)
    expect(planWrapper.vm.overallPlanPercent).toBe(100)
    expect(planWrapper.vm.sectionNavItems.map((item) => item.id)).toContain('plan-med')
    const scrollIntoView = vi.fn()
    const getElementSpy = vi.spyOn(document, 'getElementById').mockImplementation((id) =>
      id === 'plan-exercise' ? { scrollIntoView } : null,
    )
    const rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => {
      cb()
      return 1
    })
    planWrapper.vm.scrollToDimSection('exercise')
    expect(scrollIntoView).toHaveBeenCalled()
    planWrapper.vm.scrollToDimSection('missing')
    planWrapper.vm.scrollToSection('plan-history')
    expect(planWrapper.vm.activeSection).toBe('plan-history')
    getElementSpy.mockRestore()
    rafSpy.mockRestore()
    expect(planWrapper.vm.formatShortTime(new Date().toISOString())).toMatch(/\d{2}:\d{2}/)
    expect(planWrapper.vm.formatShortTime('2026-01-02T08:30:00')).toContain('1')
    const dateStringSpy = vi.spyOn(Date.prototype, 'toDateString').mockImplementationOnce(() => {
      throw new Error('bad date')
    })
    expect(planWrapper.vm.formatShortTime('bad-date')).toBe('Invalid Date')
    dateStringSpy.mockRestore()
    expect(planWrapper.vm.formatShortTime()).toBe('')
    planWrapper.vm.syncFavoriteInHistory('p1', true)

    planApi.getLatestPlan.mockRejectedValueOnce(new Error('无方案'))
    await planWrapper.vm.loadPlan()
    planApi.getPlanHistory.mockRejectedValueOnce(new Error('无历史'))
    await planWrapper.vm.loadHistory()
    planApi.getPlanDetail.mockRejectedValueOnce(new Error('详情失败'))
    await planWrapper.vm.loadHistoryPlan('bad')
    planApi.generatePlan.mockRejectedValueOnce(new Error('生成失败'))
    await planWrapper.vm.handleGenerate()
    planWrapper.vm.plan = { plan_id: 'p1', is_favorite: 1, diet_plan: {}, exercise_plan: {}, rest_plan: {} }
    planWrapper.vm.favoriteLoading = false
    planApi.togglePlanFavorite.mockResolvedValueOnce({ favorited: false })
    await planWrapper.vm.handleFavorite()
    planApi.togglePlanFavorite.mockRejectedValueOnce(new Error('收藏失败'))
    await planWrapper.vm.handleFavorite()

    localStorage.setItem('access_token', 'token')
    const healthWrapper = mountSmoke(HealthInfo)
    route.params = { id: 'a1' }
    route.fullPath = '/health-info/a1'
    healthWrapper.vm.detail = {
      article_id: 'a1',
      category: 'diet',
      tags: ['饮食营养', '控糖'],
      content: '正文'.repeat(600),
      favorited: true,
    }
    expect(healthWrapper.vm.detailTags.length).toBe(2)
    expect(healthWrapper.vm.readMinutes).toBeGreaterThan(1)
    healthWrapper.vm.searchMode = true
    healthWrapper.vm.keyword = ' '
    await healthWrapper.vm.loadArticles()
    healthWrapper.vm.searchMode = true
    healthWrapper.vm.keyword = '控糖'
    await healthWrapper.vm.loadArticles()
    healthWrapper.vm.listTab = 'recommend'
    healthWrapper.vm.searchMode = false
    await healthWrapper.vm.loadArticles()
    localStorage.removeItem('access_token')
    healthWrapper.vm.listTab = 'favorites'
    await healthWrapper.vm.loadArticles()
    localStorage.setItem('access_token', 'token')
    healthWrapper.vm.listTab = 'favorites'
    articleApi.getArticleFavorites.mockResolvedValueOnce({ list: [{ article_id: 'fav1' }], total: 1 })
    await healthWrapper.vm.loadArticles()
    healthWrapper.vm.listTab = 'all'
    healthWrapper.vm.category = ''
    await healthWrapper.vm.loadArticles()
    articleApi.getArticleFavorites.mockRejectedValueOnce(new Error('收藏失败'))
    await healthWrapper.vm.loadFavoriteIds()
    articleApi.getArticleDetail.mockResolvedValueOnce({
      article_id: 'a2',
      title: '已收藏文章',
      content: '正文',
      favorited: true,
    })
    await healthWrapper.vm.loadDetail()
    healthWrapper.vm.goDetail('a3')
    healthWrapper.vm.goHome()
    healthWrapper.vm.goList()
    healthWrapper.vm.goLogin()
    expect(healthWrapper.vm.formatDate()).toBe('')
    expect(healthWrapper.vm.formatDetailDate()).toBe('')
    expect(healthWrapper.vm.truncateTitle('', 3)).toBe('')
    expect(healthWrapper.vm.tagStyle('missing')).toBeTruthy()
    expect(healthWrapper.vm.categoryBadgeStyle('missing')).toBeTruthy()
    expect(healthWrapper.vm.formatViewCount(999)).toBe('999')
    localStorage.removeItem('access_token')
    await healthWrapper.vm.toggleFavOnList('a1')
    await healthWrapper.vm.toggleFav()
    localStorage.setItem('access_token', 'token')
    healthWrapper.vm.listTab = 'favorites'
    healthWrapper.vm.articles = [{ article_id: 'a1' }]
    healthWrapper.vm.total = 1
    articleApi.toggleArticleFavorite.mockResolvedValueOnce({ favorited: false })
    await healthWrapper.vm.toggleFavOnList('a1')
    articleApi.toggleArticleFavorite.mockRejectedValueOnce(new Error('切换失败'))
    await healthWrapper.vm.toggleFavOnList('a1')
    route.params = { id: 'a2' }
    healthWrapper.vm.favorited = false
    articleApi.toggleArticleFavorite.mockResolvedValueOnce({ favorited: true })
    await healthWrapper.vm.toggleFav()
    articleApi.toggleArticleFavorite.mockResolvedValueOnce({ favorited: false })
    await healthWrapper.vm.toggleFav()
    articleApi.toggleArticleFavorite.mockRejectedValueOnce(new Error('切换失败'))
    await healthWrapper.vm.toggleFav()
    healthWrapper.vm.readStartedAt = Date.now() - 2000
    healthWrapper.vm.flushReadEvent('a2')
    expect(healthWrapper.vm.formatViewCount(10000)).toBe('1万')
    expect(healthWrapper.vm.truncateTitle('123456', 3)).toBe('123...')

    const consultationWrapper = mountSmoke(Consultation)
    route.path = '/consultation/chat'
    route.fullPath = '/consultation/chat?session_id=mounted'
    route.query = { session_id: 'mounted', doctor_id: 'd1' }
    consultationApi.getConsultMessages.mockResolvedValueOnce([{ message_id: 'mounted', sender_type: 'doctor', sent_at: new Date().toISOString() }])
    const mountedChatWrapper = mountSmoke(Consultation)
    await flush()
    mountedChatWrapper.unmount()
    route.path = '/consultation'
    route.fullPath = '/consultation?doctor_id=d1'
    route.query = { doctor_id: 'd1' }
    consultationApi.createConsultation.mockResolvedValueOnce({ session_id: 'mounted-doc' })
    consultationApi.getConsultMessages.mockResolvedValueOnce([])
    const mountedDoctorWrapper = mountSmoke(Consultation)
    await flush()
    mountedDoctorWrapper.unmount()
    route.path = '/consultation'
    route.fullPath = '/consultation'
    route.query = {}
    const doctors = [
      { doctor_id: 'd1', name: '在线医生', status: 'online' },
      { doctor_id: 'd2', name: '离线医生', status: 'offline' },
    ]
    consultationWrapper.vm.doctors = doctors
    route.query = { doctor_id: 'd1' }
    consultationApi.createConsultation.mockResolvedValueOnce({ session_id: 'init-doc' })
    consultationApi.getConsultMessages.mockResolvedValueOnce([])
    await consultationWrapper.vm.initChatFromRoute()
    expect(consultationWrapper.vm.resolveDoctor()).toBeNull()
    expect(consultationWrapper.vm.resolveDoctor('missing')).toBeUndefined()
    expect(consultationWrapper.vm.formatDate(new Date().toISOString())).toBe('今天')
    expect(consultationWrapper.vm.formatDate(new Date(Date.now() - 86_400_000).toISOString())).toBe('昨天')
    expect(consultationWrapper.vm.formatDate('2026-01-01')).toBe('1月1日')
    consultationApi.createConsultation.mockResolvedValueOnce({})
    await expect(consultationWrapper.vm.startSession(doctors[0])).rejects.toThrow('创建会话失败')
    consultationApi.createConsultation.mockResolvedValueOnce({ session_id: 's2' })
    consultationApi.getConsultMessages.mockResolvedValueOnce([{ message_id: 'm1', sender_type: 'doctor', sent_at: new Date().toISOString() }])
    consultationApi.getAiSuggest.mockResolvedValueOnce({
      possibleDiagnoses: ['低血糖'],
      recommendedExams: ['血糖'],
      suggestedQuestions: ['如何处理'],
      treatmentStrategy: '观察',
    })
    await consultationWrapper.vm.openChat(doctors[1])
    route.query = { session_id: 's3', doctor_id: 'd1' }
    consultationApi.getConsultMessages.mockResolvedValueOnce([{ message_id: 'm3', sender_type: 'doctor', sent_at: new Date().toISOString() }])
    consultationApi.getAiSuggest.mockRejectedValueOnce(new Error('AI 建议失败'))
    await consultationWrapper.vm.initChatFromRoute()
    consultationWrapper.vm.sessionId = ''
    route.query = { session_id: 's4' }
    await expect(consultationWrapper.vm.ensureSessionId()).resolves.toBe('s4')
    consultationWrapper.vm.sessionId = ''
    route.query = { doctor_id: 'd1' }
    consultationWrapper.vm.currentDoctor = doctors[0]
    consultationApi.createConsultation.mockResolvedValueOnce({ session_id: 's-doc' })
    consultationApi.getConsultMessages.mockResolvedValueOnce([])
    await expect(consultationWrapper.vm.ensureSessionId()).resolves.toBe('s-doc')
    consultationWrapper.vm.sessionId = ''
    route.query = {}
    consultationWrapper.vm.currentDoctor = null
    consultationWrapper.vm.inputMsg = '无会话'
    await consultationWrapper.vm.sendMsg()
    consultationWrapper.vm.sessionId = 's5'
    consultationWrapper.vm.chatRef = { scrollTop: 0, scrollHeight: 100 }
    consultationWrapper.vm.inputMsg = '请问'
    consultationApi.sendConsultMessage.mockResolvedValueOnce({
      userMessage: { message_id: 'u1', sender_type: 'user', content: '请问', sent_at: new Date().toISOString() },
      aiMessage: { message_id: 'a1', sender_type: 'doctor', content: '回复', sent_at: new Date().toISOString() },
    })
    await consultationWrapper.vm.sendMsg()
    consultationWrapper.vm.inputMsg = '无AI回复'
    consultationApi.sendConsultMessage.mockResolvedValueOnce({
      userMessage: { message_id: 'u2', sender_type: 'user', content: '无AI回复', sent_at: new Date().toISOString() },
    })
    await consultationWrapper.vm.sendMsg()
    consultationWrapper.vm.inputMsg = '列表被清空'
    let resolveSend
    consultationApi.sendConsultMessage.mockReturnValueOnce(new Promise((resolve) => { resolveSend = resolve }))
    const pendingSend = consultationWrapper.vm.sendMsg()
    await flush()
    consultationWrapper.vm.messages = []
    resolveSend({
      userMessage: { message_id: 'u3', sender_type: 'user', content: '列表被清空', sent_at: new Date().toISOString() },
    })
    await pendingSend
    consultationWrapper.vm.inputMsg = '失败消息'
    consultationApi.sendConsultMessage.mockRejectedValueOnce(new Error('发送失败'))
    await consultationWrapper.vm.sendMsg()
    consultationWrapper.vm.rating = 0
    await consultationWrapper.vm.submitClose()
    consultationWrapper.vm.rating = 5
    consultationWrapper.vm.feedback = '很好'
    consultationApi.closeConsultation.mockResolvedValueOnce({ success: true })
    await consultationWrapper.vm.submitClose()
    expect(consultationWrapper.vm.statusText()).toBe('离线')
    expect(consultationWrapper.vm.probText('medium')).toBe('中等')
    expect(consultationWrapper.vm.showDateDivider(0)).toBe(true)

    userApi.getUserProfile.mockResolvedValue({ user_id: 'u3', points: 88, privacy_settings: { data_visible: false, consult_notify: false } })
    userApi.getHealthRecord.mockResolvedValue({ bmi: 22, fasting_glucose: 5.6 })
    userApi.getHealthAlert.mockResolvedValue({ level: 'normal' })
    userApi.getHealthTrendSummary.mockResolvedValue({ summary: '良好' })
    localStorage.setItem('health_trend_summary:u3', JSON.stringify({ summary: '良好', updatedAt: Date.now() }))
    userApi.getUserConsultations.mockResolvedValue({ total: 2, list: [] })
    const checkinApiForUser = await import('@/api/checkin')
    checkinApiForUser.getCheckinStats.mockResolvedValue({ completion_rate: 0.875 })
    const userWrapper = mountSmoke(UserCenter)
    await flush()
    expect(userWrapper.vm.trendSummary).toBe('良好')
    expect(userApi.getHealthTrendSummary).toHaveBeenCalled()
    expect(userWrapper.vm.quickStats[0].value).toBe('88%')
    userWrapper.vm.quickStats[0].action()
    userWrapper.vm.quickStats[1].action()
    userWrapper.vm.quickStats[2].action()
    userWrapper.vm.health = { bmi: 25, fasting_glucose: 6.2 }
    userWrapper.vm.profile = { points: 10, privacy_settings: {} }
    expect(userWrapper.vm.bmiClass).toBe('value-warn')
    expect(userWrapper.vm.glucoseClass).toBe('value-warn')
    expect(userWrapper.vm.formatMedicalHistory()).toBe('—')
    expect(userWrapper.vm.formatMedicalHistory({ medical_history: '高血压' })).toBe('高血压')
    expect(userWrapper.vm.formatMedicalHistory({ medical_histories: [{ disease_name: '糖尿病' }] })).toBe('糖尿病')
    expect(userWrapper.vm.formatMedication()).toBe('—')
    expect(userWrapper.vm.formatTime('2026-07-01T08:00:00')).toContain('2026-07-01')
    userWrapper.vm.health = { bmi: 22, fasting_glucose: 5.5 }
    expect(userWrapper.vm.bmiClass).toBe('value-normal')
    expect(userWrapper.vm.glucoseClass).toBe('value-normal')
    userWrapper.vm.health = {}
    expect(userWrapper.vm.bmiClass).toBe('')
    expect(userWrapper.vm.glucoseClass).toBe('')
    expect(userWrapper.vm.formatMedication({ medication: '二甲双胍' })).toBe('二甲双胍')
    expect(userWrapper.vm.formatMedication({ medications: [{ drug_name: '药物', dosage: '1片', frequency_desc: '每日' }] })).toBe('药物（1片 每日）')
    expect(userWrapper.vm.formatMedication({ medications: [{ drugName: '药物B' }, {}] })).toBe('药物B')
    userWrapper.vm.handleMenu({ action: 'consultations' })
    userWrapper.vm.handleMenu({ action: 'export' })
    userWrapper.vm.handleMenu({ action: 'security' })
    userWrapper.vm.handleMenu({ path: '/living-plans' })
    userWrapper.vm.onProfileSaved({ user_id: 'u4', nickname: '新用户' })
    userWrapper.vm.onHealthSaved({ bmi: 21 })
    userApi.updatePrivacySettings.mockResolvedValueOnce({ ok: true })
    await userWrapper.vm.savePrivacy()
    await userWrapper.vm.enableBrowserNotify()
    userWrapper.vm.goGlucoseCheckin()
    expect(push).toHaveBeenLastCalledWith('/checkin-records/glucose')
    userWrapper.vm.healthAlert = { plan_id: 'plan-1', has_alert: true, active: true }
    userApi.acknowledgeHealthIntervention.mockResolvedValueOnce({ ok: true })
    await userWrapper.vm.ackHealthAlert()
    expect(userWrapper.vm.healthAlert.active).toBe(false)
    userWrapper.vm.healthAlert = { planId: 'plan-2', has_alert: true, active: true }
    userApi.acknowledgeHealthIntervention.mockRejectedValueOnce(new Error('ignore'))
    await userWrapper.vm.ackHealthAlert()
    expect(userWrapper.vm.healthAlert.has_alert).toBe(false)
    userWrapper.vm.profile = null
    await userWrapper.vm.refreshHealthTrend()
    userWrapper.vm.profile = { user_id: 'u5' }
    userApi.getHealthTrendSummary.mockResolvedValueOnce({})
    await userWrapper.vm.refreshHealthTrend()
    expect(userWrapper.vm.trendLoading).toBe(false)
    userApi.getUserProfile.mockRejectedValueOnce(new Error('profile failed'))
    userApi.getHealthRecord.mockRejectedValueOnce(new Error('health failed'))
    userApi.getHealthAlert.mockRejectedValueOnce(new Error('alert failed'))
    userApi.getUserConsultations.mockRejectedValueOnce(new Error('consult failed'))
    const checkinApiForUserErrors = await import('@/api/checkin')
    checkinApiForUserErrors.getCheckinStats.mockRejectedValueOnce(new Error('stats failed'))
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    await userWrapper.vm.loadPage()
    errorSpy.mockRestore()
  })

  it('covers checkin hub, type pages, utils and validation branches', async () => {
    const checkinApi = await import('@/api/checkin')
    const reminderApi = await import('@/api/checkinReminder')
    const elementPlus = await import('element-plus')
    const echarts = await import('echarts')
    const utils = await import('../CheckinRecords/checkin/utils')
    const AchievementWall = (await import('../CheckinRecords/AchievementWall.vue')).default
    const CheckinRecords = (await import('../CheckinRecords/index.vue')).default
    const GlucoseCheckin = (await import('../CheckinRecords/GlucoseCheckin.vue')).default
    const FoodCheckin = (await import('../CheckinRecords/FoodCheckin.vue')).default
    const ExerciseCheckin = (await import('../CheckinRecords/ExerciseCheckin.vue')).default
    const MedicationCheckin = (await import('../CheckinRecords/MedicationCheckin.vue')).default

    const wallWrapper = mountSmoke(AchievementWall)
    await flush()
    expect(wallWrapper.vm.cardStyle('gold').background).toBeTruthy()
    expect(wallWrapper.vm.iconStyle('unknown').background).toBeTruthy()
    expect(wallWrapper.vm.badgeStyle('emerald').color).toBeTruthy()
    wallWrapper.vm.filter = 'unlocked'
    expect(wallWrapper.vm.filteredList.every((item) => item.unlocked)).toBe(true)
    wallWrapper.unmount()

    const hub = mountSmoke(CheckinRecords)
    await flush()

    hub.vm.todayStatus = {
      today_points: 12,
      today_checkins: [
        { checkin_type: 'diet', completed: true },
        { checkin_type: 'medication', completed: false },
        { checkin_type: 'exercise', completed: true },
        { checkin_type: 'glucose', completed: false },
      ],
    }
    hub.vm.achievementWall = {
      achievements: [{ id: 'a1', unlocked: true }, { id: 'a2', unlocked: false }],
      total: 12,
      unlockedCount: 1,
    }
    expect(hub.vm.todayProgress).toBe(50)
    expect(hub.vm.todayTasksSummary).toEqual({ done: 2, total: 4 })
    expect(hub.vm.unlockedCount).toBe(1)
    expect(hub.vm.isTypeCompleted('food')).toBe(true)
    expect(hub.vm.isTypeCompleted('glucose')).toBe(false)
    expect(hub.vm.achievementPercent).toBe(8)
    expect(hub.vm.achievementPreview).toHaveLength(2)
    expect(hub.vm.achievementPreviewEmojis).toHaveLength(1)
    hub.vm.goTypePage('food')
    expect(push).toHaveBeenLastCalledWith('/checkin-records/food')
    await hub.vm.goReminderTab({ tab: 'glucose', log_id: 'log-1' })
    expect(reminderApi.clickReminder).toHaveBeenCalledWith('log-1')
    expect(push).toHaveBeenLastCalledWith('/checkin-records/glucose')
    reminderApi.clickReminder.mockRejectedValueOnce(new Error('ignore click'))
    await hub.vm.goReminderTab({ logId: 'log-2' })
    expect(push).toHaveBeenLastCalledWith('/checkin-records/food')
    hub.vm.dismissBanner({ log_id: 'log-3' })
    expect(reminderApi.ackReminder).toHaveBeenCalledWith('log-3')
    reminderApi.ackReminder.mockRejectedValueOnce(new Error('ignore ack'))
    hub.vm.dismissBanner({ logId: 'log-4' })
    hub.vm.dismissBanner({})
    hub.unmount()

    expect(utils.calcGrams(200, 2, 1.1)).toBe(220.00000000000003)
    expect(utils.calcFoodCalories({ input_amount: 100, input_unit: 1, calories_per_gram: 1.5 })).toBe(150)
    expect(utils.calcPresetFoodCalories({ calories_per_gram: 0.5, ml_to_g_ratio: 1 }, 200, 2)).toBe(100)
    expect(utils.calcExCalories(6, 20)).toBe(120)
    expect(utils.formatFoodAmount({ input_unit: 2, input_amount: 200, grams: 210 })).toContain('ml')
    expect(utils.formatFoodAmount({ input_unit: 1, input_amount: 80 })).toBe('80g')
    expect(utils.formatTime()).toBe('')
    const imageTarget = { style: { display: 'block' } }
    utils.onImgError({ target: imageTarget })
    expect(imageTarget.style.display).toBe('none')

    const glucoseWrapper = mountSmoke(GlucoseCheckin)
    await flush()

    glucoseWrapper.vm.glucoseChartRef = document.createElement('div')
    glucoseWrapper.vm.glucoseHistory = {
      records: Array.from({ length: 9 }, (_, index) => ({
        glucose_value: 5 + index / 10,
        record_time: `2026-07-0${(index % 8) + 1} 08:00:00`,
        measure_context_label: '空腹',
      })),
      summary: { count: 9 },
    }
    glucoseWrapper.vm.renderGlucoseChart()
    const chart = echarts.init.mock.results.at(-1).value
    const chartOptions = chart.setOption.mock.calls.at(-1)[0]
    expect(chartOptions.tooltip.formatter([{ dataIndex: 0 }])).toContain('mmol/L')
    expect(chartOptions.tooltip.formatter([{ dataIndex: 99 }])).toBe('')
    expect(chartOptions.xAxis.axisLabel.rotate).toBe(35)
    expect(chartOptions.yAxis.min({ min: 3.2 })).toBe(2)
    expect(chartOptions.yAxis.max({ max: 7.2 })).toBe(9)

    checkinApi.getGlucoseHistory.mockRejectedValueOnce(new Error('趋势异常'))
    await glucoseWrapper.vm.loadGlucoseHistory()
    expect(glucoseWrapper.vm.glucoseStatusLabel('missing')).toBe('-')
    glucoseWrapper.vm.glucoseHistory = { records: [], summary: {} }
    glucoseWrapper.vm.glucoseChartRef = document.createElement('div')
    checkinApi.getGlucoseHistory.mockResolvedValueOnce({
      records: [{ glucose_value: 5.8, record_time: '', checkin_date: '2026-07-03' }],
      summary: { count: 1 },
    })
    await glucoseWrapper.vm.loadGlucoseHistory()
    checkinApi.getGlucoseRecords.mockResolvedValueOnce([{ record_id: 'g2', glucose_value: 5.9 }])
    await glucoseWrapper.vm.loadRecords()
    expect(glucoseWrapper.vm.glucoseRecords).toHaveLength(1)

    glucoseWrapper.vm.glucoseForm = { value: null, context: 4 }
    await glucoseWrapper.vm.submitGlucose()
    glucoseWrapper.vm.glucoseForm = { value: 22, context: 4 }
    elementPlus.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    await glucoseWrapper.vm.submitGlucose()
    glucoseWrapper.vm.glucoseForm = { value: 6.1, context: 4 }
    checkinApi.createGlucoseCheckin.mockRejectedValueOnce(new Error('血糖异常'))
    await glucoseWrapper.vm.submitGlucose()
    glucoseWrapper.unmount()

    const foodWrapper = mountSmoke(FoodCheckin)
    await flush()

    await foodWrapper.vm.submitFoodPreset()
    foodWrapper.vm.foodDialogFood = { food_id: 'f1', image_object_key: 'food-img' }
    foodWrapper.vm.foodDialogAmount = 100
    checkinApi.createFoodCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await foodWrapper.vm.submitFoodPreset()

    foodWrapper.vm.customFood = { category_id: '', name: '', image_object_key: '', input_amount: 100 }
    await foodWrapper.vm.submitCustomFood()
    foodWrapper.vm.customFood = {
      category_id: 'c1',
      name: ' 牛奶 ',
      image_object_key: 'img',
      input_amount: 200,
      calories_per_gram: 0.6,
      is_liquid: true,
      input_unit: 2,
      ml_to_g_ratio: 1.03,
      meal_period: 1,
      record_time: '08:00',
    }
    checkinApi.createFoodCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await foodWrapper.vm.submitCustomFood()
    expect(foodWrapper.vm.customFood.name).toBe('')

    foodWrapper.vm.foodDialogFood = { food_id: 'f2' }
    foodWrapper.vm.foodDialogAmount = 120
    foodWrapper.vm.foodDialogTime = ''
    await foodWrapper.vm.submitFoodPreset()
    foodWrapper.vm.foodDialogTime = '08:30'
    checkinApi.createFoodCheckin.mockRejectedValueOnce(new Error('饮食异常'))
    await foodWrapper.vm.submitFoodPreset()
    foodWrapper.vm.customFood = {
      category_id: 'c1',
      name: '鸡蛋',
      image_object_key: '',
      input_amount: 100,
      record_time: '08:00',
    }
    await foodWrapper.vm.submitCustomFood()
    foodWrapper.vm.customFood = {
      category_id: 'c1',
      name: '鸡蛋',
      image_object_key: 'img',
      input_amount: 0,
      record_time: '08:00',
    }
    await foodWrapper.vm.submitCustomFood()
    foodWrapper.vm.customFood = {
      category_id: 'c1',
      name: '鸡蛋',
      image_object_key: 'img',
      input_amount: 100,
      record_time: '',
    }
    await foodWrapper.vm.submitCustomFood()
    foodWrapper.vm.customFood = {
      category_id: 'c1',
      name: '鸡蛋',
      image_object_key: 'img',
      input_amount: 100,
      calories_per_gram: 1,
      is_liquid: false,
      input_unit: 2,
      ml_to_g_ratio: 1.2,
      meal_period: 1,
      record_time: '09:00',
    }
    checkinApi.createFoodCheckin.mockRejectedValueOnce(new Error('自定义饮食异常'))
    await foodWrapper.vm.submitCustomFood()
    await foodWrapper.vm.handleUpload(null, foodWrapper.vm.customFood)
    checkinApi.uploadCheckinImage.mockResolvedValueOnce({ object_key: 'food-upload', image_url: 'food.png' })
    await foodWrapper.vm.handleUpload(new File(['food'], 'food.png', { type: 'image/png' }), foodWrapper.vm.customFood)
    checkinApi.uploadCheckinImage.mockRejectedValueOnce(new Error('上传异常'))
    await foodWrapper.vm.handleUpload(new File(['bad'], 'bad.png', { type: 'image/png' }), foodWrapper.vm.customFood)

    const exerciseWrapper = mountSmoke(ExerciseCheckin)
    await flush()
    exerciseWrapper.vm.openExPreset({ exercise_id: 'e1', name: '散步' })
    expect(exerciseWrapper.vm.exDialogVisible).toBe(true)
    exerciseWrapper.vm.exDialogDuration = 0
    await exerciseWrapper.vm.submitExPreset()
    exerciseWrapper.vm.exDialogDuration = 30
    checkinApi.createExerciseCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await exerciseWrapper.vm.submitExPreset()
    exerciseWrapper.vm.openExPreset({ exercise_id: 'e2', name: '慢跑' })
    exerciseWrapper.vm.exDialogDuration = 20
    checkinApi.createExerciseCheckin.mockRejectedValueOnce(new Error('运动异常'))
    await exerciseWrapper.vm.submitExPreset()
    exerciseWrapper.vm.customEx = { name: '', calories_per_minute: 5, duration: 30 }
    await exerciseWrapper.vm.submitCustomEx()
    exerciseWrapper.vm.customEx = { name: ' 跳绳 ', calories_per_minute: 8, duration: 15 }
    checkinApi.createExerciseCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await exerciseWrapper.vm.submitCustomEx()
    exerciseWrapper.vm.customEx = { name: '慢走', calories_per_minute: 3, duration: 20 }
    checkinApi.createExerciseCheckin.mockRejectedValueOnce(new Error('自定义运动异常'))
    await exerciseWrapper.vm.submitCustomEx()
    exerciseWrapper.unmount()

    const medicationWrapper = mountSmoke(MedicationCheckin)
    await flush()
    medicationWrapper.vm.checkinDate = ''
    await medicationWrapper.vm.loadRecords()
    medicationWrapper.vm.checkinDate = '2026-07-03'
    medicationWrapper.vm.openMedPreset({ drug_id: 'm1', name: '二甲双胍' })
    expect(medicationWrapper.vm.medDialogVisible).toBe(true)
    await medicationWrapper.vm.submitMedPreset()
    medicationWrapper.vm.medDialogDosage = ' 1片 '
    checkinApi.createMedicationCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await medicationWrapper.vm.submitMedPreset()
    medicationWrapper.vm.openMedPreset({ drug_id: 'm2', name: '阿卡波糖' })
    medicationWrapper.vm.medDialogDosage = '1粒'
    checkinApi.createMedicationCheckin.mockRejectedValueOnce(new Error('用药异常'))
    await medicationWrapper.vm.submitMedPreset()
    medicationWrapper.vm.customMed = { name: '', dosage: '', taken: true, image_object_key: '' }
    await medicationWrapper.vm.submitCustomMed()
    medicationWrapper.vm.customMed = { name: '药物', dosage: '1片', taken: true, image_object_key: '' }
    await medicationWrapper.vm.submitCustomMed()
    medicationWrapper.vm.customMed = { name: ' 药物 ', dosage: ' 2片 ', taken: false, image_object_key: 'med-img' }
    checkinApi.createMedicationCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await medicationWrapper.vm.submitCustomMed()
    medicationWrapper.vm.customMed = { name: '药物', dosage: '1片', taken: true, image_object_key: 'med-img' }
    checkinApi.createMedicationCheckin.mockRejectedValueOnce(new Error('自定义用药异常'))
    await medicationWrapper.vm.submitCustomMed()
    await medicationWrapper.vm.handleUpload(null, medicationWrapper.vm.customMed)
    checkinApi.uploadCheckinImage.mockResolvedValueOnce({ object_key: 'med-upload', image_url: 'med.png' })
    await medicationWrapper.vm.handleUpload(new File(['med'], 'med.png', { type: 'image/png' }), medicationWrapper.vm.customMed)
    checkinApi.uploadCheckinImage.mockRejectedValueOnce(new Error('上传异常'))
    await medicationWrapper.vm.handleUpload(new File(['bad'], 'bad.png', { type: 'image/png' }), medicationWrapper.vm.customMed)
    medicationWrapper.unmount()

    expect(elementPlus.ElMessage.warning).toHaveBeenCalled()
    foodWrapper.unmount()
  })

  it('covers health evaluation step, chart and history branches', async () => {
    localStorage.clear()
    const riskApi = await import('@/api/risk')
    const userApi = await import('@/api/user')
    const healthRecordApi = await import('@/api/healthRecord')
    const echarts = await import('echarts')
    const HealthEvaluation = (await import('../HealthEvaluation/index.vue')).default

    const wrapper = mountSmoke(HealthEvaluation)
    await flush()

    wrapper.vm.userProfile = { user_id: 'risk-user' }
    wrapper.vm.result = { assessment_id: 'risk-1' }
    localStorage.setItem('he_risk_risk-user_unread', JSON.stringify(['risk-1']))
    wrapper.vm.readUnreadIds()
    expect(wrapper.vm.isCurrentResultUnread).toBe(true)
    expect(wrapper.vm.isUnreadAssessment({ assessment_id: 'risk-1' })).toBe(true)
    expect(wrapper.vm.latestUnreadAssessment).toBeNull()
    localStorage.setItem('he_risk_risk-user_unread', '{bad json')
    wrapper.vm.readUnreadIds()
    expect(wrapper.vm.unreadAssessmentIds).toEqual([])
    localStorage.setItem('he_risk_risk-user_pending_at', 'bad')
    wrapper.vm.syncPendingFromStorage()
    expect(wrapper.vm.assessPending).toBe(false)
    localStorage.setItem('he_risk_risk-user_pending_at', String(Date.now()))
    wrapper.vm.syncPendingFromStorage()
    expect(wrapper.vm.assessPending).toBe(true)
    wrapper.vm.switchToHistoryTab()
    expect(wrapper.vm.activeTab).toBe('history')

    userApi.getUserProfile.mockRejectedValueOnce(new Error('profile failed'))
    await wrapper.vm.loadProfile()
    expect(wrapper.vm.userProfile).toEqual({})
    healthRecordApi.getHealthRecord.mockResolvedValueOnce({})
    await wrapper.vm.loadHealthRecord()
    healthRecordApi.getHealthRecord.mockRejectedValueOnce(new Error('record failed'))
    await wrapper.vm.loadHealthRecord()

    wrapper.vm.userProfile = { gender: 2, birth_date: '2000-12-31' }
    expect(wrapper.vm.showPregnantOption).toBe(true)
    wrapper.vm.userProfile = { gender: 'male', birth_date: '2000-01-01' }
    wrapper.vm.form.diabetes_type = 4
    expect(wrapper.vm.showPregnantOption).toBe(true)
    wrapper.vm.form.diabetes_type = 1
    expect(wrapper.vm.showPregnantOption).toBe(false)
    expect(wrapper.vm.userAge).toBeGreaterThan(20)

    const rejectingRef = { validateField: vi.fn(() => Promise.reject(new Error('invalid'))), validate: vi.fn(() => Promise.reject(new Error('invalid'))) }
    const resolvingRef = { validateField: vi.fn(() => Promise.resolve(true)), validate: vi.fn(() => Promise.resolve(true)) }
    wrapper.vm.formRef = rejectingRef
    wrapper.vm.currentStep = 1
    await wrapper.vm.nextStep()
    expect(wrapper.vm.currentStep).toBe(1)
    wrapper.vm.formRef = resolvingRef
    await wrapper.vm.nextStep()
    expect(wrapper.vm.currentStep).toBe(2)
    wrapper.vm.formRef = rejectingRef
    await wrapper.vm.nextStep()
    expect(wrapper.vm.currentStep).toBe(2)
    wrapper.vm.formRef = resolvingRef
    await wrapper.vm.nextStep()
    expect(wrapper.vm.currentStep).toBe(3)
    wrapper.vm.goToStep(1)
    expect(wrapper.vm.currentStep).toBe(1)
    wrapper.vm.goToStep(2)
    expect(wrapper.vm.currentStep).toBe(1)

    expect(wrapper.vm.factorLevelClass()).toBe('')
    expect(wrapper.vm.factorLevelClass('高风险')).toContain('high')
    expect(wrapper.vm.factorLevelClass('中风险')).toContain('medium')
    expect(wrapper.vm.factorLevelClass('低风险')).toContain('low')
    expect(wrapper.vm.levelText('unknown')).toBe('unknown')
    expect(wrapper.vm.bmiLevelText()).toBe('-')
    expect(wrapper.vm.glucoseText('other')).toBe('other')
    expect(wrapper.vm.confidenceText('medium')).toBe('中')
    expect(wrapper.vm.formatTime()).toBe('-')
    expect(wrapper.vm.formatTime('2026-07-01T08:00:00')).toContain('2026')

    wrapper.vm.formRef = rejectingRef
    await wrapper.vm.submitAssess()
    expect(wrapper.vm.currentStep).toBe(1)

    wrapper.vm.gaugeRef = document.createElement('div')
    wrapper.vm.radarRef = document.createElement('div')
    wrapper.vm.formRef = resolvingRef
    riskApi.assessRisk.mockResolvedValueOnce({
      assessment_id: 'r-new',
      risk_score: 68,
      risk_level: 'medium',
      factors: [{ name: '血糖', weight: 40 }, { name: '血压', weight: 0 }],
    })
    riskApi.getRiskHistory.mockResolvedValueOnce({
      list: [{ assessment_id: 'r-new', risk_score: 68, assessed_at: new Date().toISOString() }],
    })
    localStorage.removeItem('he_risk_risk-user_pending_at')
    wrapper.vm.syncPendingFromStorage()
    await wrapper.vm.submitAssess()
    await flush()
    await flush()
    expect(wrapper.vm.assessPending).toBe(false)

    riskApi.getRiskDetail.mockResolvedValueOnce({
      risk_score: 68,
      risk_level: 'medium',
      factors: [{ name: '血糖', weight: 40 }, { name: '血压', weight: 0 }],
    })
    await wrapper.vm.viewHistory({ assessment_id: 'r-new' })
    expect(wrapper.vm.showResult).toBe(true)
    expect(wrapper.vm.result?.risk_score).toBe(68)

    echarts.init.mockClear()
    const gaugeEl = document.createElement('div')
    const radarEl = document.createElement('div')
    setSetupRef(wrapper, 'gaugeRef', gaugeEl)
    setSetupRef(wrapper, 'radarRef', radarEl)
    wrapper.vm.renderCharts()
    await flush()

    setSetupRef(wrapper, 'result', { risk_score: 20, factors: [] })
    wrapper.vm.renderCharts()
    setSetupRef(wrapper, 'gaugeRef', null)
    wrapper.vm.renderCharts()

    setSetupRef(wrapper, 'gaugeRef', document.createElement('div'))
    setSetupRef(wrapper, 'radarRef', document.createElement('div'))
    riskApi.getRiskDetail.mockResolvedValueOnce({
      risk_score: 50,
      factors: [{ name: 'BMI', weight: 30 }],
    })
    await wrapper.vm.viewHistory({ assessment_id: 'r2' })
    expect(wrapper.vm.activeTab).toBe('assess')
    await wrapper.vm.viewHistory({ risk_score: 30, factors: [] })
    expect(wrapper.vm.result.risk_score).toBe(30)
    wrapper.vm.resetQuestionnaire()
    expect(wrapper.vm.showResult).toBe(false)

    wrapper.vm.formRef = resolvingRef
    riskApi.assessRisk.mockRejectedValueOnce(new Error('提交异常'))
    await wrapper.vm.submitAssess()
    await flush()
    expect(wrapper.vm.submitting).toBe(false)
    wrapper.unmount()
  })
})
