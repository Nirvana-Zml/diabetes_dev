import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { flush, mountView, route, routerPush, routerReplace } from './test-utils'

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  saveTokens: vi.fn(),
  clearTokens: vi.fn(),
  isLoggedIn: vi.fn(() => false),
  isAdmin: vi.fn(() => false),
  getStatsOverview: vi.fn(),
  getStatsTrends: vi.fn(),
  getStatsUsers: vi.fn(),
  getStatsUserBrief: vi.fn(),
  getAdminVideos: vi.fn(),
  getAdminVideoDetail: vi.fn(),
  createAdminVideo: vi.fn(),
  updateAdminVideo: vi.fn(),
  deleteAdminVideo: vi.fn(),
  uploadAdminVideoCover: vi.fn(),
  uploadAdminVideoFile: vi.fn(),
  getAdminArticles: vi.fn(),
  getAdminArticleDetail: vi.fn(),
  createAdminArticle: vi.fn(),
  updateAdminArticle: vi.fn(),
  uploadAdminArticleCover: vi.fn(),
  deleteAdminArticle: vi.fn(),
  submitAdminArticle: vi.fn(),
  reviewAdminArticle: vi.fn(),
  getPendingReviewArticles: vi.fn(),
  generateArticleDraft: vi.fn(),
  getAdminAuditLogs: vi.fn(),
  getAdminAuditLogDetail: vi.fn(),
  getAdminAuditActions: vi.fn(),
  deleteAdminAuditLog: vi.fn(),
  batchDeleteAdminAuditLogs: vi.fn(),
  exportAdminAuditLogs: vi.fn(),
  getAdminAuditOverview: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  confirm: vi.fn(() => Promise.resolve()),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush, replace: routerReplace }),
  useRoute: () => route,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: mocks.success,
      error: mocks.error,
      warning: mocks.warning,
    },
    ElMessageBox: { confirm: mocks.confirm },
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
  saveTokens: mocks.saveTokens,
  clearTokens: mocks.clearTokens,
}))

vi.mock('@/utils/auth', () => ({
  isLoggedIn: mocks.isLoggedIn,
  isAdmin: mocks.isAdmin,
}))

vi.mock('@/api/stats', () => ({
  getStatsOverview: mocks.getStatsOverview,
  getStatsTrends: mocks.getStatsTrends,
  getStatsUsers: mocks.getStatsUsers,
  getStatsUserBrief: mocks.getStatsUserBrief,
}))

vi.mock('@/api/video', () => ({
  getAdminVideos: mocks.getAdminVideos,
  getAdminVideoDetail: mocks.getAdminVideoDetail,
  createAdminVideo: mocks.createAdminVideo,
  updateAdminVideo: mocks.updateAdminVideo,
  deleteAdminVideo: mocks.deleteAdminVideo,
  uploadAdminVideoCover: mocks.uploadAdminVideoCover,
  uploadAdminVideoFile: mocks.uploadAdminVideoFile,
}))

vi.mock('@/utils/media', () => ({
  videoCoverUrl: vi.fn((id) => `/covers/${id}.jpg`),
}))

vi.mock('@/api/article', () => ({
  getAdminArticles: mocks.getAdminArticles,
  getAdminArticleDetail: mocks.getAdminArticleDetail,
  createAdminArticle: mocks.createAdminArticle,
  updateAdminArticle: mocks.updateAdminArticle,
  uploadAdminArticleCover: mocks.uploadAdminArticleCover,
  deleteAdminArticle: mocks.deleteAdminArticle,
  submitAdminArticle: mocks.submitAdminArticle,
  reviewAdminArticle: mocks.reviewAdminArticle,
  getPendingReviewArticles: mocks.getPendingReviewArticles,
  generateArticleDraft: mocks.generateArticleDraft,
  categoryMap: { diet: '饮食' },
  ARTICLE_STATUS_LABELS: { draft: '草稿' },
}))

vi.mock('@/api/audit', () => ({
  getAdminAuditLogs: mocks.getAdminAuditLogs,
  getAdminAuditLogDetail: mocks.getAdminAuditLogDetail,
  getAdminAuditActions: mocks.getAdminAuditActions,
  deleteAdminAuditLog: mocks.deleteAdminAuditLog,
  batchDeleteAdminAuditLogs: mocks.batchDeleteAdminAuditLogs,
  exportAdminAuditLogs: mocks.exportAdminAuditLogs,
  getAdminAuditOverview: mocks.getAdminAuditOverview,
}))

beforeEach(() => {
  vi.clearAllMocks()
  route.query = {}
  route.path = '/home'
  global.URL.createObjectURL = vi.fn(() => 'blob:preview')
  mocks.getStatsOverview.mockResolvedValue({
    users: { total: 10, active_week: 3, new_today: 1, new_week: 2 },
    checkin: { total: 20, today: 2 },
    consultation: { total_sessions: 5, active_sessions: 1, total_messages: 8, avg_rating: 4.5 },
    health: { total_records: 6, total_assessments: 4 },
    plan: { total_plans: 3, users_with_plans: 2 },
    content: { published_articles: 7, total_reads: 100, total_videos: 4 },
    messages: { total: 9, unread: 1 },
    distributions: {
      gender: [{ label: '男', value: 5 }],
      age_group: [{ label: '30-40', value: 3 }],
      diabetes_type: [{ label: '2型', value: 4 }],
      risk_level: [{ label: '中', value: 2 }],
      checkin_type: [{ label: '饮食', value: 6 }],
      message_type: [{ label: '系统', value: 1 }],
    },
  })
  mocks.getStatsTrends.mockResolvedValue({
    user_registration: [{ date: '2026-07-01', value: 2 }],
    daily_checkin: [{ date: '2026-07-02', value: 5 }],
  })
  mocks.getStatsUsers.mockResolvedValue({ users: [{ username: 'u1' }], total: 1 })
  mocks.getStatsUserBrief.mockResolvedValue({ subject_id: 'u1', username: 'user1', nickname: '用户1', role: 'user' })
  mocks.getAdminVideos.mockResolvedValue({ list: [{ video_id: 'v1', title: '视频1', cover_url: '' }] })
  mocks.getAdminVideoDetail.mockResolvedValue({ video_id: 'v1', title: '视频1', duration: '01:00', cover_url: '/c.jpg', video_url: '/v.mp4' })
  mocks.createAdminVideo.mockResolvedValue({ video_id: 'v-new' })
  mocks.updateAdminVideo.mockResolvedValue({ video_id: 'v1' })
  mocks.uploadAdminVideoCover.mockResolvedValue({ cover_url: '/new-cover.jpg' })
  mocks.uploadAdminVideoFile.mockImplementation(async (_id, _file, onProgress) => {
    onProgress?.({ loaded: 50, total: 100 })
    return { duration: '02:00', video_url: '/new.mp4' }
  })
  mocks.getAdminArticles.mockResolvedValue({ list: [{ article_id: 'a1', title: '文章', status: 'draft', created_at: '2026-07-01' }] })
  mocks.getPendingReviewArticles.mockResolvedValue({ list: [{ article_id: 'a2', title: '待审', status: 'pending' }] })
  mocks.getAdminArticleDetail.mockResolvedValue({
    article_id: 'a1',
    title: '文章',
    summary: '摘要',
    category: 'diet',
    content: '# 正文',
    tags: ['控糖'],
    cover_image: '/cover.jpg',
  })
  mocks.createAdminArticle.mockResolvedValue({ article_id: 'a-new' })
  mocks.generateArticleDraft.mockImplementation(async (_input, callbacks) => {
    callbacks.onChunk?.({ title: 'AI标题', summary: 'AI摘要', content: 'AI正文', tags: ['AI'] })
    callbacks.onComplete?.({ title: 'AI标题', summary: 'AI摘要', content: 'AI正文', tags: ['AI'] })
    return { title: 'AI标题', content: 'AI正文' }
  })
  mocks.getAdminAuditActions.mockResolvedValue([{ action: 'login', label: '登录' }])
  mocks.getAdminAuditLogs.mockResolvedValue({
    list: [{
      log_id: 'log1',
      user_id: 'u1',
      action: 'login',
      resource: '/auth/login',
      result: 1,
      ip_address: '127.0.0.1',
      user_agent: 'vitest',
      created_at: '2026-07-01T10:00:00',
      detail: { ok: true },
    }],
    total: 1,
  })
  mocks.getAdminAuditLogDetail.mockResolvedValue({ log_id: 'log1', action: 'login', result: 0, detail: 'failed' })
  mocks.getAdminAuditOverview.mockResolvedValue({
    today: { total: 10, failed: 2, success: 8 },
    action_distribution: [{ action: 'login', label: '登录', count: 5 }],
    login_failure_trend: [{ date: '2026-07-01', count: 6 }],
    top_users: [{ user_id: 'u1', count: 3 }],
    top_admins: [{ user_id: 'admin1', count: 2 }],
  })
  mocks.login.mockResolvedValue({ role: 'admin', accessToken: 'token' })
})

describe('admin views smoke', () => {
  it('mounts login and handles auth flow', async () => {
    const Login = (await import('../Login/index.vue')).default
    const wrapper = mountView(Login)
    await flush()

    mocks.isLoggedIn.mockReturnValueOnce(true)
    mocks.isAdmin.mockReturnValueOnce(true)
    route.query.redirect = '/articles'
    wrapper.unmount()
    mountView(Login)
    await flush()
    expect(routerReplace).toHaveBeenCalled()

    const loginWrapper = mountView(Login)
    loginWrapper.vm.formRef = { validate: vi.fn(async () => false) }
    await loginWrapper.vm.handleSubmit()
    loginWrapper.vm.formRef = { validate: vi.fn(async () => true) }
    loginWrapper.vm.form.username = ' admin '
    loginWrapper.vm.form.password = 'pass'
    mocks.login.mockResolvedValueOnce({ role: 'user' })
    await loginWrapper.vm.handleSubmit()
    expect(mocks.clearTokens).toHaveBeenCalled()

    mocks.login.mockResolvedValueOnce({ role: 'admin' })
    route.query.redirect = '//evil.com'
    await loginWrapper.vm.handleSubmit()
    expect(routerReplace).toHaveBeenCalledWith('/home')

    mocks.login.mockRejectedValueOnce(new Error('bad login'))
    await loginWrapper.vm.handleSubmit()

    routerReplace.mockRejectedValueOnce(new Error('nav fail'))
    mocks.login.mockResolvedValueOnce({ role: 'admin' })
    route.query.redirect = '/statistics'
    await loginWrapper.vm.handleSubmit()
    loginWrapper.unmount()
  })

  it('mounts home and handles overview and navigation', async () => {
    const Home = (await import('../Home/index.vue')).default
    const wrapper = mountView(Home)
    await flush()
    expect(wrapper.text()).toContain('数据统计')

    await wrapper.vm.loadOverview()
    expect(mocks.getStatsOverview).toHaveBeenCalled()
    mocks.getStatsOverview.mockRejectedValueOnce(new Error('stats fail'))
    await wrapper.vm.loadOverview()

    await wrapper.vm.handleLogout()
    expect(routerPush).toHaveBeenCalledWith('/login')
    wrapper.vm.modules[0] && wrapper.vm.modules[0].path
    await wrapper.find('.module-card').trigger('click')
    wrapper.unmount()
  })

  it('mounts statistics page branches', async () => {
    const Statistics = (await import('../Statistics/index.vue')).default
    route.query.userId = 'u-focus'
    const wrapper = mountView(Statistics)
    await flush()

    await wrapper.vm.reload()
    await wrapper.vm.loadTrends()
    await wrapper.vm.onPageSizeChange()
    wrapper.vm.clearFocusedUser()
    expect(routerReplace).toHaveBeenCalled()

    route.query.userId = 'missing'
    mocks.getStatsUserBrief.mockRejectedValueOnce(new Error('missing'))
    await wrapper.vm.loadFocusedUser()

    route.query.userId = 123
    await wrapper.vm.loadFocusedUser()

    mocks.getStatsOverview.mockRejectedValueOnce(new Error('reload fail'))
    await wrapper.vm.reload()

    wrapper.vm.overview = null
    expect(wrapper.vm.summaryCards).toEqual([])
    expect(wrapper.vm.ratingHint(null)).toBe('暂无评分')
    expect(wrapper.vm.ratingHint(4.2)).toBe('均分 4.2')
    expect(wrapper.vm.formatDateLabel('')).toBe('')
    expect(wrapper.vm.formatDateLabel('2026-07-01')).toBe('07-01')
    expect(wrapper.vm.formatDateLabel('0701')).toBe('0701')
    wrapper.unmount()
  })

  it('mounts videos page and CRUD helpers', async () => {
    const Videos = (await import('../Videos/index.vue')).default
    const wrapper = mountView(Videos)
    await flush()

    expect(wrapper.vm.coverUrl({ cover_url: '/x.jpg' })).toBe('/x.jpg')
    expect(wrapper.vm.coverUrl({ video_id: 'v1' })).toBe('/covers/v1.jpg')
    expect(wrapper.vm.formatDate('')).toBe('—')
    expect(wrapper.vm.formatDate('2026-07-01T12:00:00')).toContain('2026-07-01')

    wrapper.vm.openCreate()
    wrapper.vm.formRef = { validate: vi.fn(async () => false) }
    await wrapper.vm.handleSave()

    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    wrapper.vm.form.title = '新视频'
    wrapper.vm.coverFile = new File(['c'], 'c.png', { type: 'image/png' })
    wrapper.vm.videoFile = new File(['v'], 'v.mp4', { type: 'video/mp4' })
    await wrapper.vm.handleSave()

    await wrapper.vm.openEdit({ video_id: 'v1', title: '视频1' })
    wrapper.vm.editingId = 'v1'
    wrapper.vm.form.title = '更新'
    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    await wrapper.vm.handleSave()

    wrapper.vm.onCoverChange({ raw: new File(['c'], 'c.png', { type: 'image/png' }) })
    wrapper.vm.onCoverChange({ raw: null })
    wrapper.vm.onVideoChange({ raw: new File(['x'], 'x.avi', { type: 'video/avi' }) })
    wrapper.vm.onVideoChange({ raw: new File(['v'], 'v.mp4', { type: 'video/mp4' }) })
    wrapper.vm.clearCover()
    wrapper.vm.resetForm()

    await wrapper.vm.handleDelete({ video_id: 'v1', title: '视频1' })
    mocks.getAdminVideos.mockRejectedValueOnce(new Error('list fail'))
    await wrapper.vm.loadList()
    mocks.getAdminVideoDetail.mockRejectedValueOnce(new Error('detail fail'))
    await wrapper.vm.openEdit({ video_id: 'v1' })
    mocks.updateAdminVideo.mockRejectedValueOnce(new Error('save fail'))
    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    wrapper.vm.editingId = 'v1'
    await wrapper.vm.handleSave()
    mocks.deleteAdminVideo.mockRejectedValueOnce(new Error('delete fail'))
    await wrapper.vm.handleDelete({ video_id: 'v1', title: '视频1' })
    wrapper.unmount()
  })

  it('mounts articles page and content workflows', async () => {
    const Articles = (await import('../Articles/index.vue')).default
    const wrapper = mountView(Articles)
    await flush()

    await wrapper.vm.loadPending()
    wrapper.vm.openCreate()
    wrapper.vm.contentMode = 'preview'
    wrapper.vm.form.content = '# md'
    wrapper.vm.formRef = { validate: vi.fn(async () => false) }
    await wrapper.vm.handleSave()

    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    wrapper.vm.form.title = '标题'
    wrapper.vm.form.summary = '摘要'
    wrapper.vm.form.category = 'diet'
    wrapper.vm.form.content = '正文'
    wrapper.vm.form.tagsText = '控糖,饮食'
    wrapper.vm.coverFile = new File(['img'], 'a.png', { type: 'image/png' })
    await wrapper.vm.handleSave()

    await wrapper.vm.openEdit({ article_id: 'a1' })
    wrapper.vm.editingId = 'a1'
    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    await wrapper.vm.handleSave()
    await wrapper.vm.handleSubmit({ article_id: 'a1' })
    await wrapper.vm.handleReview({ article_id: 'a1' }, 'approve')
    await wrapper.vm.handleReview({ article_id: 'a1' }, 'reject')
    wrapper.vm.openReject({ article_id: 'a1' })
    await wrapper.vm.confirmReject()
    wrapper.vm.rejectReason = '原因'
    await wrapper.vm.confirmReject()

    wrapper.vm.handleAiGenerate()
    wrapper.vm.aiForm.topic = ''
    await wrapper.vm.startAiGenerate()
    wrapper.vm.aiForm.topic = '主题'
    await wrapper.vm.startAiGenerate()
    wrapper.vm.aiDraft.content = ''
    wrapper.vm.applyAiDraft()
    wrapper.vm.aiDraft.content = '正文'
    wrapper.vm.applyAiDraft()
    wrapper.vm.aiGenerating = false
    wrapper.vm.resetAiDialog()

    wrapper.vm.handleCoverChange({ raw: new File(['t'], 't.txt', { type: 'text/plain' }) })
    const bigFile = new File(['big'], 'big.png', { type: 'image/png' })
    Object.defineProperty(bigFile, 'size', { value: 6 * 1024 * 1024 })
    wrapper.vm.handleCoverChange({ raw: bigFile })
    wrapper.vm.handleCoverChange({ raw: new File(['ok'], 'ok.png', { type: 'image/png' }) })
    wrapper.vm.clearCover()

    expect(wrapper.vm.statusTagType('published')).toBe('success')
    expect(wrapper.vm.statusTagType('unknown')).toBe('info')
    expect(wrapper.vm.formatDate('2026-07-01')).toBe('2026-07-01')
    expect(wrapper.vm.formatDate('')).toBe('—')

    mocks.getAdminArticles.mockRejectedValueOnce(new Error('list fail'))
    await wrapper.vm.loadList()
    mocks.getAdminArticleDetail.mockRejectedValueOnce(new Error('detail fail'))
    await wrapper.vm.openEdit({ article_id: 'a1' })
    mocks.createAdminArticle.mockRejectedValueOnce(new Error('save fail'))
    wrapper.vm.formRef = { validate: vi.fn(async () => true) }
    await wrapper.vm.handleSave()
    mocks.deleteAdminArticle.mockRejectedValueOnce(new Error('delete fail'))
    await wrapper.vm.handleDelete({ article_id: 'a1', title: '文章' })
    mocks.submitAdminArticle.mockRejectedValueOnce(new Error('submit fail'))
    await wrapper.vm.handleSubmit({ article_id: 'a1' })
    mocks.reviewAdminArticle.mockRejectedValueOnce(new Error('review fail'))
    await wrapper.vm.confirmReject()
    mocks.generateArticleDraft.mockRejectedValueOnce(new Error('ai fail'))
    wrapper.vm.aiForm.topic = '主题'
    await wrapper.vm.startAiGenerate()
    wrapper.unmount()
  })

  it('mounts audit logs and overview', async () => {
    const AuditLogs = (await import('../AuditLogs/index.vue')).default
    const wrapper = mountView(AuditLogs)
    await flush()

    expect(wrapper.vm.formatDate(null)).toBe('—')
    expect(wrapper.vm.formatDate([2026, 7, 1, 10, 5, 3])).toBe('2026-07-01 10:05:03')
    expect(wrapper.vm.formatDate('2026-07-01T10:00:00')).toContain('2026-07-01')
    expect(wrapper.vm.formatDetail(null)).toBe('—')
    expect(wrapper.vm.formatDetail('raw')).toBe('raw')
    expect(wrapper.vm.formatDetail({ a: 1 })).toContain('"a"')
    expect(wrapper.vm.roleLabel('admin')).toBe('管理员')
    expect(wrapper.vm.roleLabel('user')).toBe('用户')
    expect(wrapper.vm.roleLabel('guest')).toBe('guest')

    wrapper.vm.onActionChange()
    wrapper.vm.applyPreset('today')
    wrapper.vm.applyPreset('week')
    wrapper.vm.applyPreset('failed')
    wrapper.vm.applyPreset('login')
    wrapper.vm.onSelectionChange([{ log_id: 'log1' }])
    wrapper.vm.onSizeChange()
    wrapper.vm.resetFilters()
    wrapper.vm.goStatistics('u1')
    await wrapper.vm.loadUserBrief('u1')
    await wrapper.vm.loadUserBrief('u1')
    mocks.getStatsUserBrief.mockRejectedValueOnce(new Error('brief fail'))
    await wrapper.vm.loadUserBrief('u2')
    await wrapper.vm.handleExport()
    await wrapper.vm.openDetail({ log_id: 'log1' })
    await wrapper.vm.handleDelete({ log_id: 'log1' })
    wrapper.vm.selectedIds = ['log1']
    await wrapper.vm.handleBatchDelete()
    wrapper.vm.selectedIds = []
    await wrapper.vm.handleBatchDelete()

    mocks.getAdminAuditActions.mockRejectedValueOnce(new Error('actions fail'))
    await wrapper.vm.loadActions()
    mocks.getAdminAuditLogs.mockRejectedValueOnce(new Error('logs fail'))
    await wrapper.vm.loadList()
    mocks.exportAdminAuditLogs.mockRejectedValueOnce(new Error('export fail'))
    await wrapper.vm.handleExport()
    mocks.getAdminAuditLogDetail.mockRejectedValueOnce(new Error('detail fail'))
    await wrapper.vm.openDetail({ log_id: 'log1' })
    mocks.deleteAdminAuditLog.mockRejectedValueOnce(new Error('delete fail'))
    await wrapper.vm.handleDelete({ log_id: 'log1' })
    wrapper.vm.selectedIds = ['log1']
    mocks.batchDeleteAdminAuditLogs.mockRejectedValueOnce(new Error('batch fail'))
    await wrapper.vm.handleBatchDelete()
    wrapper.unmount()

    const AuditOverview = (await import('../AuditLogs/AuditOverview.vue')).default
    const overview = mountView(AuditOverview)
    await flush()
    await overview.vm.loadOverview()
    overview.vm.overview = null
    expect(overview.vm.failureRate).toBe(0)
    overview.vm.overview = { today: { total: 0, failed: 0, success: 0 }, action_distribution: [], login_failure_trend: [] }
    expect(overview.vm.pieGradient).toContain('conic-gradient')
    expect(overview.vm.trendBarHeight(0)).toBe('0%')
    expect(overview.vm.trendBarHeight(3)).toContain('%')
    overview.vm.overview = {
      today: { total: 5, failed: 1, success: 4 },
      action_distribution: [{ action: 'login', count: 2 }],
      login_failure_trend: [{ date: '2026-07-01', count: 6 }],
      top_users: [{ user_id: 'u1', count: 1 }],
      top_admins: [{ user_id: 'a1', count: 1 }],
    }
    expect(overview.vm.actionDistribution[0].label).toBe('login')
    expect(overview.vm.formatDateLabel('2026-07-01')).toBe('07-01')
    mocks.getAdminAuditOverview.mockRejectedValueOnce(new Error('overview fail'))
    await overview.vm.loadOverview()
    overview.unmount()
  })
})
