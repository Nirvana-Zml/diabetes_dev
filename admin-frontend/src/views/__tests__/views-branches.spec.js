import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { flush, mountView, route, routerReplace } from './test-utils'

const mocks = vi.hoisted(() => ({
  getStatsOverview: vi.fn(),
  getStatsTrends: vi.fn(),
  getStatsUsers: vi.fn(),
  getStatsUserBrief: vi.fn(),
  getAdminAuditOverview: vi.fn(),
  getAdminAuditLogs: vi.fn(),
  getAdminAuditLogDetail: vi.fn(),
  getAdminAuditActions: vi.fn(),
  deleteAdminAuditLog: vi.fn(),
  batchDeleteAdminAuditLogs: vi.fn(),
  exportAdminAuditLogs: vi.fn(),
  getAdminVideos: vi.fn(),
  login: vi.fn(),
  error: vi.fn(),
  createAdminArticle: vi.fn(async () => ({ article_id: 'a-new' })),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: routerReplace }),
  useRoute: () => route,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mocks.error, warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

vi.mock('@element-plus/icons-vue', () => ({
  User: { template: '<i />' },
  Lock: { template: '<i />' },
  Upload: { template: '<i />' },
  Document: { template: '<i />' },
  VideoCamera: { template: '<i />' },
  DataAnalysis: { template: '<i />' },
  ArrowRight: { template: '<i />' },
  Notebook: { template: '<i />' },
}))

vi.mock('@/api/auth', () => ({
  login: mocks.login,
  saveTokens: vi.fn(),
  clearTokens: vi.fn(),
}))

vi.mock('@/utils/auth', () => ({
  isLoggedIn: vi.fn(() => false),
  isAdmin: vi.fn(() => false),
}))

vi.mock('@/api/stats', () => ({
  getStatsOverview: mocks.getStatsOverview,
  getStatsTrends: mocks.getStatsTrends,
  getStatsUsers: mocks.getStatsUsers,
  getStatsUserBrief: mocks.getStatsUserBrief,
}))

vi.mock('@/api/audit', () => ({
  getAdminAuditOverview: mocks.getAdminAuditOverview,
  getAdminAuditLogs: mocks.getAdminAuditLogs,
  getAdminAuditLogDetail: mocks.getAdminAuditLogDetail,
  getAdminAuditActions: mocks.getAdminAuditActions,
  deleteAdminAuditLog: mocks.deleteAdminAuditLog,
  batchDeleteAdminAuditLogs: mocks.batchDeleteAdminAuditLogs,
  exportAdminAuditLogs: mocks.exportAdminAuditLogs,
}))

vi.mock('@/api/video', () => ({
  getAdminVideos: mocks.getAdminVideos,
  getAdminVideoDetail: vi.fn(),
  createAdminVideo: vi.fn(),
  updateAdminVideo: vi.fn(),
  deleteAdminVideo: vi.fn(),
  uploadAdminVideoCover: vi.fn(),
  uploadAdminVideoFile: vi.fn(),
}))

vi.mock('@/utils/media', () => ({
  videoCoverUrl: vi.fn((id) => `/media/${id}.jpg`),
}))

vi.mock('@/api/article', () => ({
  getAdminArticles: vi.fn(async () => ({ list: [] })),
  getAdminArticleDetail: vi.fn(async () => ({
    article_id: 'a1',
    title: 't',
    summary: 's',
    category: 'diet',
    content: 'c',
    tags: 'tag1,tag2',
  })),
  createAdminArticle: mocks.createAdminArticle,
  updateAdminArticle: vi.fn(),
  uploadAdminArticleCover: vi.fn(async () => ({ cover_image: '/uploaded-cover.jpg' })),
  deleteAdminArticle: vi.fn(async () => ({})),
  submitAdminArticle: vi.fn(),
  reviewAdminArticle: vi.fn(async () => ({})),
  getPendingReviewArticles: vi.fn(async () => ({ list: [] })),
  generateArticleDraft: vi.fn(),
  categoryMap: {},
  ARTICLE_STATUS_LABELS: {},
}))

beforeEach(() => {
  vi.clearAllMocks()
  route.query = {}
  mocks.getStatsOverview.mockResolvedValue({
    users: { total: 8, active_week: 2, new_today: 1, new_week: 3 },
    checkin: { total: 12, today: 2 },
    consultation: { total_sessions: 4, active_sessions: 1, total_messages: 6, avg_rating: null },
    health: { total_records: 5, total_assessments: 2 },
    plan: { total_plans: 1, users_with_plans: 1 },
    content: { published_articles: 3, total_reads: 20, total_videos: 2 },
    messages: { total: 4, unread: 0 },
    distributions: {},
  })
  mocks.getStatsTrends.mockResolvedValue({ user_registration: [], daily_checkin: [] })
  mocks.getStatsUsers.mockResolvedValue({ users: [], total: 0 })
  mocks.getStatsUserBrief.mockResolvedValue({ subject_id: 'u1', username: 'u1', nickname: '用户', role: 'user' })
  mocks.getAdminAuditOverview.mockResolvedValue({
    today: { total: 0, failed: 0, success: 0 },
    action_distribution: [],
    login_failure_trend: [],
    top_users: [],
    top_admins: [],
  })
  mocks.getAdminAuditLogs.mockResolvedValue({ list: [], total: 0 })
  mocks.getAdminAuditActions.mockResolvedValue([])
  mocks.getAdminVideos.mockResolvedValue({ list: [] })
})

describe('admin views branches', () => {
  it('covers home overview card branches', async () => {
    const Home = (await import('../Home/index.vue')).default
    const wrapper = mountView(Home)
    await flush()

    wrapper.vm.overview = null
    expect(wrapper.vm.overviewCards.every((card) => card.value === '-')).toBe(true)

    wrapper.vm.overview = {}
    expect(wrapper.vm.overviewCards.every((card) => card.value === 0)).toBe(true)

    wrapper.vm.overview = {
      users: { total: 1, active_week: 2, new_today: 0, new_week: 0 },
      checkin: { total: 3, today: 0 },
      consultation: { total_sessions: 4, active_sessions: 0, total_messages: 0, avg_rating: 4.5 },
      health: { total_records: 0, total_assessments: 0 },
      plan: { total_plans: 0, users_with_plans: 0 },
      content: { published_articles: 0, total_reads: 0, total_videos: 0 },
      messages: { total: 0, unread: 0 },
    }
    expect(wrapper.vm.overviewCards[0].value).toBe(1)

    mocks.getStatsOverview.mockRejectedValueOnce({})
    await wrapper.vm.loadOverview()
    expect(mocks.error).toHaveBeenCalledWith('统计数据加载失败')
    wrapper.unmount()
  })

  it('covers statistics helper and pagination error branches', async () => {
    const Statistics = (await import('../Statistics/index.vue')).default
    route.query.userId = 'u1'
    const wrapper = mountView(Statistics)
    await flush()

    wrapper.vm.overview = mocks.getStatsOverview.mock.results[0]?.value
    expect(wrapper.vm.summaryCards.length).toBeGreaterThan(0)
    expect(wrapper.vm.ratingHint(undefined)).toBe('暂无评分')

    mocks.error.mockClear()
    mocks.getStatsUsers.mockRejectedValueOnce({})
    wrapper.vm.page = 2
    wrapper.vm.onPageSizeChange()
    await flush()
    expect(mocks.error).toHaveBeenCalledWith('加载用户列表失败')

    wrapper.vm.overview = null
    expect(wrapper.vm.distributions).toEqual({})

    wrapper.vm.overview = { users: { total: 1 } }
    expect(wrapper.vm.distributions).toEqual({})

    mocks.getStatsOverview.mockRejectedValueOnce({})
    await wrapper.vm.reload()
    expect(mocks.error).toHaveBeenCalledWith('加载统计数据失败')

    route.query.userId = 'u1'
    await wrapper.vm.loadFocusedUser()
    expect(wrapper.vm.focusedUser).toBeTruthy()

    wrapper.vm.trends = { user_registration: [{ date: '2026-07-01', value: 'x' }], daily_checkin: [{ date: 'short', value: null }] }
    wrapper.vm.focusedUser = { nickname: 'Nick', username: 'user', subject_id: 'u1' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.registrationTrend[0].value).toBe(0)

    mocks.getStatsUsers.mockResolvedValueOnce({ total: 0 })
    await wrapper.vm.loadUsers()
    expect(wrapper.vm.users).toEqual([])
    wrapper.unmount()
  })

  it('covers distribution bar label fallback branches', async () => {
    const DistributionBars = (await import('../Statistics/DistributionBars.vue')).default
    const wrapper = mount(DistributionBars, {
      props: { items: [{ key: 'only-key', value: 2 }, { label: '', value: 0 }] },
    })
    expect(wrapper.text()).toContain('only-key')
    expect(wrapper.findAll('.bar-row').length).toBe(2)
  })

  it('covers distribution bar fallback label dash', async () => {
    const DistributionBars = (await import('../Statistics/DistributionBars.vue')).default
    const wrapper = mount(DistributionBars, {
      props: { items: [{ value: 3 }] },
    })
    expect(wrapper.text()).toContain('-')
  })
  it('covers distribution bar empty items fallback', async () => {
    const DistributionBars = (await import('../Statistics/DistributionBars.vue')).default
    const wrapper = mount(DistributionBars, {
      props: { items: null },
    })
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('covers login fallback error message', async () => {
    const Login = (await import('../Login/index.vue')).default
    const wrapper = mountView(Login)
    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    wrapper.vm.form.username = 'admin'
    wrapper.vm.form.password = 'pass'
    mocks.login.mockRejectedValueOnce({})
    await wrapper.vm.handleSubmit()
    expect(mocks.error).toHaveBeenCalledWith('登录失败')
    wrapper.unmount()
  })

  it('covers articles delete/review success and tag parsing branches', async () => {
    const articleApi = await import('@/api/article')
    const Articles = (await import('../Articles/index.vue')).default
    const wrapper = mountView(Articles)
    await flush()

    await wrapper.vm.openEdit({ article_id: 'a1' })
    expect(wrapper.vm.form.tagsText).toBe('tag1,tag2')

    await wrapper.vm.handleDelete({ article_id: 'a1', title: 't' })
    await wrapper.vm.handleReview({ article_id: 'a1' }, 'approve')

    articleApi.reviewAdminArticle.mockRejectedValueOnce(new Error('review fail'))
    await wrapper.vm.handleReview({ article_id: 'a1' }, 'reject')

    articleApi.getPendingReviewArticles.mockRejectedValueOnce(new Error('pending fail'))
    await wrapper.vm.loadPending()

    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    wrapper.vm.form.title = '标题'
    wrapper.vm.form.summary = '摘要'
    wrapper.vm.form.category = 'diet'
    wrapper.vm.form.content = '正文'
    wrapper.vm.coverFile = new File(['img'], 'cover.png', { type: 'image/png' })
    await wrapper.vm.handleSave()
    expect(wrapper.vm.coverPreview).toBe('/uploaded-cover.jpg')

    wrapper.unmount()
  })

  it('covers article ai stream partial chunk branches', async () => {
    const articleApi = await import('@/api/article')
    articleApi.generateArticleDraft.mockImplementationOnce(async (_input, callbacks) => {
      callbacks.onChunk?.({ content: 'only-content' })
      callbacks.onComplete?.({ tags: ['t1'] })
      return { content: 'only-content' }
    })
    const Articles = (await import('../Articles/index.vue')).default
    const wrapper = mountView(Articles)
    await flush()
    wrapper.vm.handleAiGenerate()
    wrapper.vm.aiForm.topic = '主题'
    await wrapper.vm.startAiGenerate()
    wrapper.vm.list = [
      { article_id: 'd1', title: '草稿', status: 'draft', created_at: '2026-07-01' },
      { article_id: 'p1', title: '待审', status: 'pending', created_at: '2026-07-01' },
      { article_id: 'u1', title: '已发', status: 'published', created_at: '2026-07-01' },
      { article_id: 'r1', title: '驳回', status: 'rejected', created_at: '2026-07-01' },
    ]
    wrapper.vm.contentMode = 'preview'
    wrapper.vm.form.content = ''
    wrapper.vm.aiGenerating = true
    wrapper.vm.aiDraft.content = 'keep'
    wrapper.vm.resetAiDialog()
    expect(wrapper.vm.aiDraft.content).toBe('keep')
    wrapper.vm.aiGenerating = false
    wrapper.vm.resetAiDialog()
    expect(wrapper.vm.aiDraft.content).toBe('')
    await wrapper.vm.$nextTick()
    wrapper.unmount()
  })

  it('covers articles template visibility flags', async () => {
    const Articles = (await import('../Articles/index.vue')).default
    const wrapper = mountView(Articles)
    await flush()
    wrapper.vm.list = [
      { article_id: '1', title: 'A', status: 'draft', created_at: '2026-01-01' },
      { article_id: '2', title: 'B', status: 'pending', created_at: '2026-01-01' },
      { article_id: '3', title: 'C', status: 'published', created_at: '2026-01-01' },
      { article_id: '4', title: 'D', status: 'rejected', created_at: '2026-01-01' },
    ]
    wrapper.vm.dialogVisible = true
    wrapper.vm.rejectVisible = true
    wrapper.vm.aiDialogVisible = true
    wrapper.vm.aiGenerating = true
    wrapper.vm.coverPreview = '/cover.jpg'
    wrapper.vm.contentMode = 'preview'
    wrapper.vm.form.content = '# Title'
    wrapper.vm.aiDraft.title = 'AI'
    wrapper.vm.aiDraft.summary = 'S'
    wrapper.vm.aiDraft.content = 'C'
    wrapper.vm.aiDraft.tags = ['t']
    wrapper.vm.editingId = 'edit-1'
    wrapper.vm.saving = true
    await wrapper.vm.$nextTick()
    wrapper.unmount()
  })

  it('covers videos and audit logs fallback branches with rendered rows', async () => {
    mocks.getAdminVideos.mockResolvedValueOnce({
      list: [
        { video_id: 'v1', title: '有封面', cover_url: '/c.jpg', duration: '01:00', created_at: '2026-07-01T10:00:00' },
        { video_id: 'v2', title: '无封面', cover_url: '', duration: '', created_at: '' },
      ],
    })

    const Videos = (await import('../Videos/index.vue')).default
    const videos = mountView(Videos)
    await flush()
    expect(videos.text()).toContain('有封面')
    expect(videos.text()).toContain('无封面')

    const videoApi = await import('@/api/video')
    videoApi.getAdminVideoDetail.mockRejectedValueOnce({})
    await videos.vm.openEdit({ video_id: 'v2', title: '无封面' })

    mocks.getAdminVideos.mockRejectedValueOnce({})
    await videos.vm.loadList()

    videos.vm.formRef = { validate: vi.fn(async () => true) }
    videos.vm.form.title = '保存'
    videoApi.updateAdminVideo.mockRejectedValueOnce({})
    videos.vm.editingId = 'v1'
    await videos.vm.handleSave()
    videoApi.deleteAdminVideo.mockRejectedValueOnce({})
    await videos.vm.handleDelete({ video_id: 'v1', title: 't' })
    videos.unmount()

    mocks.getAdminAuditLogs.mockResolvedValue({
      list: [
        { log_id: 'l1', user_id: 'u1', action: 'login', resource: '/login', result: 1, created_at: [2026, 7, 1, 9, 0, 0] },
        { log_id: 'l2', user_id: 'u2', action: 'logout', resource: '/logout', result: 0, created_at: '2026-07-02T10:00:00' },
      ],
      total: 2,
    })

    const AuditLogs = (await import('../AuditLogs/index.vue')).default
    const audit = mountView(AuditLogs)
    await flush()
    audit.vm.list = [
      { log_id: 'l1', user_id: 'u1', action: 'login', resource: '/login', result: 1, created_at: [2026, 7, 1, 9, 0, 0] },
      { log_id: 'l2', user_id: 'u2', action: 'logout', resource: '/logout', result: 0, created_at: '2026-07-02T10:00:00' },
    ]
    await audit.vm.$nextTick()
    expect(audit.vm.list[0].result).toBe(1)

    mocks.getAdminAuditLogs.mockRejectedValueOnce({})
    await audit.vm.loadList()
    mocks.exportAdminAuditLogs.mockRejectedValueOnce({})
    await audit.vm.handleExport()
    mocks.getAdminAuditLogDetail.mockRejectedValueOnce({})
    await audit.vm.openDetail({ log_id: 'l1' })
    mocks.deleteAdminAuditLog.mockRejectedValueOnce({})
    await audit.vm.handleDelete({ log_id: 'l1' })
    audit.vm.selectedIds = ['l1']
    mocks.batchDeleteAdminAuditLogs.mockRejectedValueOnce({})
    await audit.vm.handleBatchDelete()
    audit.unmount()
  })

  it('covers audit overview chart branches', async () => {
    const AuditOverview = (await import('../AuditLogs/AuditOverview.vue')).default
    const wrapper = mountView(AuditOverview)
    await flush()

    expect(wrapper.vm.pieGradient).toContain('#e7e5e4')
    expect(wrapper.vm.failureRate).toBe(0)
    expect(wrapper.vm.formatDateLabel('')).toBe('')
    expect(wrapper.vm.formatDateLabel('short')).toBe('short')
    expect(wrapper.vm.actionBarItems).toEqual([])
    expect(wrapper.vm.loginTrend).toEqual([])

    mocks.getAdminAuditOverview.mockRejectedValueOnce({})
    await wrapper.vm.loadOverview()
    expect(mocks.error).toHaveBeenCalledWith('审计概览加载失败')

    wrapper.vm.overview = {
      today: { total: 10, failed: 2, success: 8 },
      action_distribution: [{ action: 'custom_action', count: 4 }],
      login_failure_trend: [{ date: '2026-07-01', count: 6 }],
      top_users: [{ user_id: 'u1', count: 2 }],
      top_admins: [{ user_id: 'a1', count: 1 }],
    }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.failureRate).toBe(20)
    expect(wrapper.vm.pieGradient).toContain('conic-gradient')
    expect(wrapper.vm.trendBarHeight(6)).toContain('%')
    wrapper.unmount()
  })
})
