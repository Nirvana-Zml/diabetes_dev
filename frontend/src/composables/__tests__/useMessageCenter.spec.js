import { beforeEach, describe, expect, it, vi } from 'vitest'

const notificationInstance = vi.hoisted(() => ({
  close: vi.fn(),
  captured: null,
}))

vi.mock('element-plus', () => ({
  ElNotification: vi.fn((opts) => {
    notificationInstance.captured = opts
    const buttons = opts.message?.children?.[1]?.children
    if (Array.isArray(buttons)) {
      for (const button of buttons) {
        if (typeof button?.children === 'function') button.children()
        else if (button?.children?.default) button.children.default()
      }
    }
    return { close: notificationInstance.close }
  }),
  ElButton: { name: 'ElButton', props: ['type', 'size'] },
}))

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    currentRoute: { value: { path: '/home', query: {} } },
  },
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({
    profile: { privacy_settings: { message_notify: true } },
  })),
}))

vi.mock('@/api/message', () => ({
  getMessages: vi.fn(async () => ({ list: [], unread_count: 0 })),
  getUnreadMessageCount: vi.fn(async () => ({ unread_count: 0 })),
  markMessageRead: vi.fn(async () => ({})),
  markMessagesReadByBiz: vi.fn(async () => ({})),
}))

vi.mock('@/utils/notification', () => ({
  isNotificationSupported: vi.fn(() => false),
  getNotificationPermission: vi.fn(() => 'default'),
  sendNotification: vi.fn(),
}))

async function flush() {
  await Promise.resolve()
  await Promise.resolve()
}

function buttonLabelFromNotification(captured) {
  const button = captured.message.children[1]?.children?.[0]
  if (typeof button.children === 'function') return button.children()
  if (button.children?.default) return button.children.default()
  return null
}

describe('useMessageCenter', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    notificationInstance.captured = null
    const routerModule = await import('@/router')
    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
    const mod = await import('../useMessageCenter')
    mod.unreadCount.value = 0
    mod.messageList.value = []
    mod.useMessageCenter().stop()
  })

  it('loads list and polls unread messages', async () => {
    const messageApi = await import('@/api/message')
    const { unreadCount, messageList, useMessageCenter } = await import('../useMessageCenter')

    messageApi.getUnreadMessageCount.mockImplementation(async () => ({ unread_count: 1 }))
    messageApi.getMessages.mockImplementation(async (params = {}) => {
      if (params.unreadOnly) {
        return {
          list: [{
            message_id: 'm2',
            message_type: 'plan_generate',
            summary: '您的方案已生成',
            link_path: '/living-plans',
          }],
        }
      }
      return {
        list: [{ message_id: 'm1', title: '方案就绪' }],
        unread_count: 1,
      }
    })

    const { loadList, start, stop } = useMessageCenter()
    await loadList()
    expect(messageList.value).toHaveLength(1)
    expect(unreadCount.value).toBe(1)

    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalled()
    notificationInstance.captured.message.children[1].children[0].props.onClick({ stopPropagation: vi.fn() })
    expect(messageApi.markMessageRead).toHaveBeenCalledWith('m2')
    const routerModule = await import('@/router')
    expect(routerModule.default.push).toHaveBeenCalledWith({ path: '/living-plans', query: {} })
    stop()
  })

  it('respects privacy settings and suppresses current page notifications', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValueOnce({
      profile: { privacy_settings: { message_notify: false } },
    })
    const { loadList } = useMessageCenter()
    await loadList()
    expect(messageApi.getMessages).not.toHaveBeenCalled()

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })

    const routerModule = await import('@/router')
    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's1' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm3',
        message_type: 'consult_reply',
        biz_id: 's1',
        is_read: false,
        summary: '医生回复了',
        link_path: '/consultation',
        link_query: { session_id: 's1' },
      }],
    })

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).not.toHaveBeenCalled()
    expect(messageApi.markMessagesReadByBiz).toHaveBeenCalledWith('consult_reply', 's1')
    stop()
  })

  it('marks consult session read', async () => {
    const messageApi = await import('@/api/message')
    const { useMessageCenter } = await import('../useMessageCenter')

    const center = useMessageCenter()
    await center.markConsultSessionRead('s9')
    expect(messageApi.markMessagesReadByBiz).toHaveBeenCalledWith('consult_reply', 's9')
    center.stop()
  })

  it('covers notification titles, suppression and browser push paths', async () => {
    const messageApi = await import('@/api/message')
    const notification = await import('@/utils/notification')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { consult_notify: true } },
    })

    messageApi.getUnreadMessageCount.mockImplementation(async () => ({ unread_count: 1 }))
    messageApi.getMessages.mockImplementation(async (params = {}) => {
      if (params.unreadOnly) {
        return {
          list: [
            { message_id: 'm-fail', message_type: 'checkin_analysis', status: 'failed', summary: '分析失败' },
            { message_id: 'm-read', is_read: true, summary: '已读' },
            {
              message_id: 'm-same',
              message_type: 'plan_generate',
              summary: '同页',
              link_path: '/health-info',
            },
          ],
        }
      }
      return { list: [], unread_count: 0 }
    })

    routerModule.default.currentRoute.value = { path: '/health-info', query: {} }
    const { start, stop } = useMessageCenter()
    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalled()
    const titles = ElNotification.mock.calls.map((call) => call[0].title)
    expect(titles).toContain('打卡分析失败')

    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    messageApi.getMessages.mockImplementation(async () => ({
      list: [{
        message_id: 'm-browser',
        message_type: 'consult_reply',
        summary: '医生回复',
        link_path: '/consultation',
        link_query: { session_id: 's3' },
      }],
    }))

    await useMessageCenter().refresh()
    await flush()
    expect(notification.sendNotification).toHaveBeenCalled()

    document.dispatchEvent(new Event('visibilitychange'))
    await flush()
    stop()
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  it('covers failed titles, close ack, poll guards and camelCase fields', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacySettings: { message_notify: true } },
    })

    messageApi.getUnreadMessageCount.mockImplementation(async () => ({ unread_count: 1 }))
    messageApi.getMessages.mockImplementation(async (params = {}) => {
      if (params.unreadOnly) {
        return {
          list: [{
            messageId: 'm-fail2',
            messageType: 'risk_assess',
            status: 'failed',
            summary: '风险失败',
            linkPath: '/health-evaluation',
            linkQuery: { tab: 'assess' },
          }],
        }
      }
      return { list: [], unread_count: 0 }
    })
    messageApi.markMessageRead.mockRejectedValueOnce(new Error('read failed'))

    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    const center = useMessageCenter()
    center.start()
    await flush()

    const { ElNotification } = await import('element-plus')
    const titles = ElNotification.mock.calls.map((call) => call[0].title)
    expect(titles).toContain('风险评估失败')

    notificationInstance.captured.onClose()
    notificationInstance.captured.message.children[1].children[0].props.onClick({ stopPropagation: vi.fn() })
    await flush()
    expect(routerModule.default.push).toHaveBeenCalledWith({
      path: '/health-evaluation',
      query: { tab: 'assess' },
    })

    center.stop()
    await center.refresh()
    expect(messageApi.getMessages).toHaveBeenCalledTimes(1)

    messageApi.getUnreadMessageCount.mockRejectedValueOnce(new Error('poll failed'))
    center.start()
    await flush()
    center.stop()
  })

  it('suppresses consult messages on the same session page', async () => {
    const messageApi = await import('@/api/message')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    routerModule.default.currentRoute.value = { path: '/consultation', query: { sessionId: 's8' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-consult',
        message_type: 'consult_reply',
        biz_id: 's8',
        summary: '同会话',
        link_path: '/consultation',
      }],
    })

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).not.toHaveBeenCalled()
    stop()
  })

  it('ignores markConsultSessionRead without session id or when api fails', async () => {
    const messageApi = await import('@/api/message')
    const { useMessageCenter } = await import('../useMessageCenter')
    const center = useMessageCenter()

    await center.markConsultSessionRead('')
    expect(messageApi.markMessagesReadByBiz).not.toHaveBeenCalled()

    messageApi.markMessagesReadByBiz.mockRejectedValueOnce(new Error('biz failed'))
    await center.markConsultSessionRead('s7')
    center.stop()
  })

  it('covers consult-only notify setting, action labels and page query suppression', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { consult_notify: true } },
    })

    routerModule.default.currentRoute.value = { path: '/living-plans', query: { plan_id: 'p1' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-plan',
        message_type: 'plan_generate',
        status: 'failed',
        title: '自定义失败',
        summary: '方案失败',
        link_path: '/living-plans',
        link_query: { plan_id: 'p1' },
      }],
    })

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).not.toHaveBeenCalled()

    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    messageApi.getMessages.mockResolvedValue({
      list: [
        { message_id: 'm-failed', message_type: 'health_alert', status: 'failed', summary: '预警失败', link_path: '/health-info' },
        { message_id: 'm-consult', message_type: 'consult_reply', summary: '医生回复了', link_path: '/consultation' },
        { message_id: 'm-view', message_type: 'checkin_analysis', summary: '分析完成', link_path: '/checkin-analysis' },
      ],
    })
    await useMessageCenter().refresh()
    await flush()

    expect(ElNotification.mock.calls.map((call) => call[0].title)).toEqual(
      expect.arrayContaining(['健康预警', '医生已回复', '打卡分析已更新']),
    )

    for (const call of ElNotification.mock.calls) {
      expect(['返回重试', '进入会话', '立即查看']).toContain(buttonLabelFromNotification(call[0]))
    }

    const consultCall = ElNotification.mock.calls.find((call) => call[0].title === '医生已回复')
    consultCall[0].message.children[1].children[0].props.onClick({ stopPropagation: vi.fn() })
    await flush()
    expect(routerModule.default.push).toHaveBeenCalledWith({ path: '/consultation', query: {} })
    stop()
  })

  it('auto marks consult unread on page and refreshes on visibility', async () => {
    const messageApi = await import('@/api/message')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's-auto' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-auto',
        message_type: 'consult_reply',
        biz_id: 's-auto',
        is_read: false,
        summary: '自动已读',
      }],
    })
    messageApi.markMessagesReadByBiz.mockRejectedValueOnce(new Error('auto mark failed'))

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    document.dispatchEvent(new Event('visibilitychange'))
    await flush()
    stop()
  })

  it('disables polling when only consult_notify is turned off', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const { unreadCount, useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { consult_notify: false } },
    })

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    expect(unreadCount.value).toBe(0)
    expect(messageApi.getMessages).not.toHaveBeenCalled()
    stop()
  })

  it('falls back to consult_notify and default privacy enablement', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacySettings: { consult_notify: true } },
    })
    const center = useMessageCenter()
    await center.loadList()
    expect(messageApi.getMessages).toHaveBeenCalledTimes(1)

    userStore.useUserStore.mockReturnValue({ profile: {} })
    await center.loadList()
    expect(messageApi.getMessages).toHaveBeenCalledTimes(2)
    center.stop()
  })

  it('covers remaining notification and polling branches', async () => {
    const messageApi = await import('@/api/message')
    const notification = await import('@/utils/notification')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { unreadCount, useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: false } },
    })
    const disabled = useMessageCenter()
    disabled.start()
    disabled.start()
    await flush()
    expect(unreadCount.value).toBe(0)
    disabled.stop()

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })

    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 0 })
    const idle = useMessageCenter()
    idle.start()
    await flush()
    idle.stop()

    routerModule.default.currentRoute.value = { path: '/living-plans', query: { plan_id: 'p1' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unreadCount: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-query-mismatch',
        message_type: 'plan_generate',
        summary: '方案',
        linkPath: '/living-plans',
        linkQuery: { plan_id: 'p2' },
      }],
    })
    const mismatch = useMessageCenter()
    mismatch.start()
    await flush()
    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalled()
    mismatch.stop()
    ElNotification.mockClear()

    routerModule.default.currentRoute.value = { path: '/living-plans', query: { plan_id: 'p3', tab: 'detail' } }
    messageApi.getMessages.mockResolvedValue({
      list: [{
        messageId: 'm-query-match',
        messageType: 'plan_generate',
        title: '自定义标题',
        summary: '',
        linkPath: '/living-plans',
        linkQuery: { plan_id: 'p3', tab: 'detail' },
      }],
    })
    const matched = useMessageCenter()
    matched.start()
    await flush()
    expect(ElNotification).not.toHaveBeenCalled()
    matched.stop()
    ElNotification.mockClear()

    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [
        { message_id: 'm-fallback', message_type: 'unknown_type', status: 'failed', title: '自定义失败' },
        { message_id: 'm-no-id', summary: '无ID' },
        { is_read: true, message_id: 'm-read2', summary: '已读' },
        { message_id: 'm-dup', summary: '重复' },
        { message_id: 'm-dup', summary: '重复2' },
      ],
    })
    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    const visible = useMessageCenter()
    visible.start()
    await flush()
    notificationInstance.captured.onClose()
    visible.stop()

    notification.sendNotification.mockClear()
    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    messageApi.getUnreadMessageCount.mockImplementation(async () => ({ unread_count: 1 }))
    messageApi.getMessages.mockImplementation(async () => ({
      list: [{
        message_id: 'm-browser2',
        message_type: 'consult_reply',
        summary: '浏览器通知',
        link_path: '/consultation',
        link_query: { session_id: 's99' },
      }],
    }))

    const browserCenter = useMessageCenter()
    browserCenter.start()
    await flush()
    await flush()
    expect(notification.sendNotification).toHaveBeenCalled()
    const browserOpts = notification.sendNotification.mock.calls.at(-1)[0]
    browserOpts.onClick()
    await flush()
    expect(routerModule.default.push).toHaveBeenCalledWith({
      path: '/consultation',
      query: { session_id: 's99' },
    })
    browserCenter.stop()

    routerModule.default.currentRoute.value = { path: '/consultation', query: { sessionId: 's-auto2' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-auto2',
        message_type: 'consult_reply',
        bizId: 's-auto2',
        isRead: false,
        summary: '自动已读成功',
      }],
    })
    const autoMark = useMessageCenter()
    autoMark.start()
    await flush()
    expect(messageApi.markMessagesReadByBiz).toHaveBeenCalledWith('consult_reply', 's-auto2')
    autoMark.stop()

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  it('covers title fallbacks, duplicate guards and dismiss actions', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })

    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    messageApi.getUnreadMessageCount.mockImplementation(async () => ({ unread_count: 1 }))
    messageApi.getMessages.mockImplementation(async () => ({
      list: [
        { message_id: 'm-unknown-fail', message_type: 'custom', status: 'failed', summary: '失败', link_path: '/health-info' },
        { message_id: 'm-unknown-ok', message_type: 'custom', title: '自定义通知', summary: '内容', link_path: '/consultation' },
        { message_id: 'm-dup-guard', summary: '重复1', link_path: '/living-plans' },
        { message_id: 'm-dup-guard', summary: '重复2', link_path: '/living-plans' },
        { summary: '无ID消息', link_path: '/checkin-analysis' },
      ],
    }))

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification.mock.calls.map((call) => call[0].title)).toEqual(
      expect.arrayContaining(['任务失败', '自定义通知', '消息通知']),
    )

    const actionCall = ElNotification.mock.calls.find((call) => call[0].title === '自定义通知')
    actionCall[0].message.children[1].children[0].props.onClick({ stopPropagation: vi.fn() })
    await flush()
    notificationInstance.captured.onClose()
    stop()
  })

  it('covers browser url, hidden without push and empty summary fallback', async () => {
    const messageApi = await import('@/api/message')
    const notification = await import('@/utils/notification')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })

    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    notification.sendNotification.mockClear()

    messageApi.getUnreadMessageCount.mockImplementation(async () => ({ unread_count: 1 }))
    messageApi.getMessages.mockImplementation(async () => ({
      list: [
        { message_id: 'm-no-query', message_type: 'plan_generate', title: '仅标题', link_path: '/living-plans' },
        { message_id: 'm-no-query', message_type: 'plan_generate', title: '重复浏览器', link_path: '/living-plans' },
      ],
    }))

    const browserCenter = useMessageCenter()
    browserCenter.start()
    await flush()
    await flush()
    expect(notification.sendNotification).toHaveBeenCalledTimes(1)
    const browserOpts = notification.sendNotification.mock.calls[0][0]
    expect(browserOpts.data.url).toBe('/living-plans')
    browserCenter.stop()

    notification.isNotificationSupported.mockReturnValue(false)
    messageApi.getMessages.mockImplementation(async () => ({
      list: [{ message_id: 'm-hidden-skip', summary: '隐藏无推送', link_path: '/health-info' }],
    }))
    notification.sendNotification.mockClear()
    const hiddenCenter = useMessageCenter()
    hiddenCenter.start()
    await flush()
    const { ElNotification } = await import('element-plus')
    expect(ElNotification).not.toHaveBeenCalled()
    expect(notification.sendNotification).not.toHaveBeenCalled()
    hiddenCenter.stop()

    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  it('shows consult notifications when session does not match current page', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })

    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 'other' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-other-session',
        message_type: 'consult_reply',
        biz_id: 's-diff',
        summary: '其他会话',
        link_path: '/consultation',
        link_query: { session_id: 's-diff' },
      }],
    })

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalled()
    stop()
  })

  it('skips auto mark when consult unread is absent', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })

    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's-no-unread' } }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-read-consult',
        message_type: 'consult_reply',
        biz_id: 's-other',
        is_read: true,
        summary: '已读咨询',
      }],
    })

    const { start, stop } = useMessageCenter()
    start()
    await flush()
    expect(messageApi.markMessagesReadByBiz).not.toHaveBeenCalled()
    stop()
  })

  it('covers dismiss-by-action onClose and empty summary in notifications', async () => {
    const messageApi = await import('@/api/message')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { useMessageCenter } = await import('../useMessageCenter')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })

    routerModule.default.currentRoute.value = { path: '/other-page', query: {} }
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-empty-summary',
        message_type: 'health_alert',
        title: '标题兜底',
        summary: '',
        link_path: '/health-info',
      }],
    })

    const { start, stop } = useMessageCenter()
    start()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalled()
    notificationInstance.captured.message.children[1].children[0].props.onClick({ stopPropagation: vi.fn() })
    notificationInstance.captured.onClose()
    stop()
  })

  it('covers internal notification helpers and remaining poll branches', async () => {
    const messageApi = await import('@/api/message')
    const notification = await import('@/utils/notification')
    const userStore = await import('@/stores/user')
    const routerModule = await import('@/router')
    const { messageCenterTestUtils, useMessageCenter } = await import('../useMessageCenter')

    expect(messageCenterTestUtils.notificationTitle({
      message_type: 'unknown',
      status: 'failed',
      title: '自定义',
    })).toBe('自定义')
    expect(messageCenterTestUtils.actionLabel({ message_type: 'plan_generate' })).toBe('立即查看')

    expect(messageCenterTestUtils.shouldSuppressOnCurrentPage({
      link_path: '/living-plans',
      link_query: { plan_id: null },
    })).toBe(false)

    routerModule.default.currentRoute.value = { path: '/living-plans', query: { plan_id: undefined } }
    expect(messageCenterTestUtils.shouldSuppressOnCurrentPage({
      link_path: '/living-plans',
      link_query: { plan_id: null },
    })).toBe(true)

    messageCenterTestUtils.showInAppNotification({ summary: '无ID' })
    messageCenterTestUtils.showInAppNotification({
      message_id: 'm-title-only',
      message_type: 'plan_generate',
      title: '仅标题',
    })
    messageCenterTestUtils.showInAppNotification({
      message_id: 'm-empty-body',
      message_type: 'plan_generate',
    })
    messageCenterTestUtils.showInAppNotification({
      message_id: 'm-title-only',
      summary: '重复',
    })

    messageCenterTestUtils.showBrowserNotification({
      message_id: 'm-browser-internal',
      message_type: 'plan_generate',
      title: '浏览器标题',
      link_path: '/living-plans',
    })
    messageCenterTestUtils.showBrowserNotification({
      message_id: 'm-browser-internal',
      summary: '重复浏览器',
    })
    messageCenterTestUtils.showBrowserNotification({
      summary: '无浏览器ID',
      link_path: '/home',
    })
    messageCenterTestUtils.showBrowserNotification({
      message_id: 'm-browser-title',
      message_type: 'plan_generate',
      title: '仅浏览器标题',
      link_path: '/living-plans',
    })
    messageCenterTestUtils.showBrowserNotification({
      message_id: 'm-browser-empty',
      message_type: 'plan_generate',
      link_path: '/living-plans',
    })
    expect(notification.sendNotification.mock.calls.at(-1)[0].body).toBe('')

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })
    messageApi.getUnreadMessageCount.mockResolvedValue({ unreadCount: 2 })
    messageApi.getMessages.mockResolvedValue({ list: [] })
    messageCenterTestUtils.setRunning(true)
    await messageCenterTestUtils.pollOnce()
    expect(messageApi.getUnreadMessageCount).toHaveBeenCalled()

    messageApi.getUnreadMessageCount.mockReset()
    messageApi.getUnreadMessageCount
      .mockResolvedValueOnce({ unread_count: 1 })
      .mockResolvedValueOnce({ unreadCount: 3 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-refresh-camel',
        message_type: 'consult_reply',
        biz_id: 's-refresh-camel',
        is_read: false,
      }],
    })
    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's-refresh-camel' } }
    messageCenterTestUtils.setRunning(true)
    await messageCenterTestUtils.pollOnce()

    await messageCenterTestUtils.openMessage({ link_path: '/health-info' })
    expect(routerModule.default.push).toHaveBeenCalled()

    routerModule.default.currentRoute.value = { path: '/consultation', query: {} }
    await messageCenterTestUtils.autoMarkConsultOnPage([])

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })
    messageApi.getUnreadMessageCount
      .mockResolvedValueOnce({ unreadCount: 2 })
      .mockResolvedValueOnce({ unreadCount: 1 })
    messageApi.getMessages.mockResolvedValueOnce({})

    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's-mark' } }
    await messageCenterTestUtils.autoMarkConsultOnPage([{
      message_type: 'consult_reply',
      biz_id: 's-mark',
      is_read: false,
    }])

    const pollCenter = useMessageCenter()
    pollCenter.start()
    await flush()
    pollCenter.stop()

    messageApi.getMessages.mockResolvedValueOnce({ unread_count: 1 })
    await useMessageCenter().loadList()

    userStore.useUserStore.mockReturnValue({
      profile: { privacy_settings: { message_notify: true } },
    })
    messageApi.getUnreadMessageCount.mockImplementation(async () => ({ unreadCount: 1 }))
    messageApi.getMessages.mockResolvedValue({
      list: [{ message_id: 'm-poll-camel', summary: '轮询', link_path: '/home' }],
    })
    const camelPoll = useMessageCenter()
    camelPoll.start()
    await flush()
    camelPoll.stop()

    messageApi.getUnreadMessageCount.mockReset()
    messageApi.getUnreadMessageCount
      .mockResolvedValueOnce({ unread_count: 1 })
      .mockResolvedValueOnce({ unreadCount: 0 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-consult-refresh',
        message_type: 'consult_reply',
        biz_id: 's-refresh',
        is_read: false,
        summary: '刷新未读',
      }],
    })
    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's-refresh' } }
    const refreshCenter = useMessageCenter()
    refreshCenter.start()
    await flush()
    refreshCenter.stop()

    messageApi.getMessages.mockResolvedValueOnce({ unread_count: 2 })
    await useMessageCenter().loadList()
    expect(messageApi.getMessages).toHaveBeenCalled()

    notification.sendNotification.mockClear()
    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    messageApi.getUnreadMessageCount.mockResolvedValue({ unread_count: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{ message_id: 'm-hidden-noop', summary: '隐藏', link_path: '/home' }],
    })
    const hidden = useMessageCenter()
    hidden.start()
    await flush()
    hidden.stop()
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })
})
