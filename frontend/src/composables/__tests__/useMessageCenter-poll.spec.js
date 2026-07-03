import { beforeEach, describe, expect, it, vi } from 'vitest'

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
  getMessages: vi.fn(async () => ({ list: [] })),
  getUnreadMessageCount: vi.fn(async () => ({ unread_count: 0 })),
  markMessageRead: vi.fn(async () => ({})),
  markMessagesReadByBiz: vi.fn(async () => ({})),
}))

vi.mock('@/utils/notification', () => ({
  isNotificationSupported: vi.fn(() => false),
  getNotificationPermission: vi.fn(() => 'default'),
  sendNotification: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElNotification: vi.fn(() => ({ close: vi.fn() })),
  ElButton: { name: 'ElButton', props: ['type', 'size'] },
}))

async function flush() {
  await Promise.resolve()
  await Promise.resolve()
}

describe('useMessageCenter poll branch coverage', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    const mod = await import('../useMessageCenter')
    mod.useMessageCenter().stop()
  })

  it('reads unread count from camelCase and refreshes after consult auto mark', async () => {
    const messageApi = await import('@/api/message')
    const routerModule = await import('@/router')
    const { messageCenterTestUtils } = await import('../useMessageCenter')

    messageApi.getUnreadMessageCount
      .mockResolvedValueOnce({ unreadCount: 2 })
      .mockResolvedValueOnce({ unreadCount: 1 })
    messageApi.getMessages.mockResolvedValue({ list: [] })
    messageCenterTestUtils.setRunning(true)
    await messageCenterTestUtils.pollOnce()

    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's-final' } }
    messageApi.getUnreadMessageCount
      .mockResolvedValueOnce({ unread_count: 1 })
      .mockResolvedValueOnce({ unreadCount: 2 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        message_id: 'm-final',
        message_type: 'consult_reply',
        biz_id: 's-final',
        is_read: false,
      }],
    })
    messageCenterTestUtils.setRunning(true)
    await messageCenterTestUtils.pollOnce()
  })

  it('uses camelCase-only fields in browser notification and consult auto mark', async () => {
    const messageApi = await import('@/api/message')
    const notification = await import('@/utils/notification')
    const routerModule = await import('@/router')
    const { messageCenterTestUtils } = await import('../useMessageCenter')

    messageCenterTestUtils.showBrowserNotification({
      messageId: 'm-camel-type-only',
      messageType: 'plan_generate',
      linkPath: '/living-plans',
    })
    expect(notification.sendNotification.mock.calls.at(-1)[0].tag).toContain('plan_generate')

    routerModule.default.currentRoute.value = { path: '/consultation', query: { sessionId: 's-camel-only' } }
    messageApi.markMessagesReadByBiz.mockClear()
    await messageCenterTestUtils.autoMarkConsultOnPage([{
      messageType: 'consult_reply',
      bizId: 's-camel-only',
      isRead: false,
    }])
    expect(messageApi.markMessagesReadByBiz).toHaveBeenCalledWith('consult_reply', 's-camel-only')
  })

  it('pollOnce reads unreadCount when unread_count is absent', async () => {
    const messageApi = await import('@/api/message')
    const { messageCenterTestUtils, unreadCount } = await import('../useMessageCenter')

    expect(messageCenterTestUtils.resolveUnreadCount({ unreadCount: 9 })).toBe(9)
    expect(messageCenterTestUtils.resolveUnreadCount({ unread_count: 4 })).toBe(4)

    messageApi.getUnreadMessageCount.mockResolvedValue({ unreadCount: 9 })
    messageApi.getMessages.mockResolvedValue({ list: [] })
    messageCenterTestUtils.setRunning(true)
    await messageCenterTestUtils.pollOnce()
    expect(unreadCount.value).toBe(9)
  })

  it('reads unread count from camelCase only during poll refresh', async () => {
    const messageApi = await import('@/api/message')
    const routerModule = await import('@/router')
    const { messageCenterTestUtils, unreadCount } = await import('../useMessageCenter')

    messageApi.getUnreadMessageCount
      .mockResolvedValueOnce({ unreadCount: 2 })
      .mockResolvedValueOnce({ unreadCount: 1 })
    messageApi.getMessages.mockResolvedValue({
      list: [{
        messageId: 'm-poll-camel-only',
        messageType: 'consult_reply',
        bizId: 's-poll-camel',
        isRead: false,
      }],
    })
    routerModule.default.currentRoute.value = { path: '/consultation', query: { session_id: 's-poll-camel' } }
    messageCenterTestUtils.setRunning(true)
    await messageCenterTestUtils.pollOnce()
    expect(unreadCount.value).toBe(1)
  })
})
