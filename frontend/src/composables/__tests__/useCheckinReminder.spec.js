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

vi.mock('@/api/checkinReminder', () => ({
  getPendingReminders: vi.fn(async () => []),
  ackReminder: vi.fn(async () => null),
  snoozeReminder: vi.fn(async () => null),
  clickReminder: vi.fn(async () => null),
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

describe('useCheckinReminder', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    notificationInstance.captured = null
    const routerModule = await import('@/router')
    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
    const mod = await import('../useCheckinReminder')
    mod.pendingReminders.value = []
    mod.useCheckinReminder().stop()
  })

  it('polls pending reminders and shows in-app notification', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const { ElNotification } = await import('element-plus')
    const { pendingReminders, useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log1', tab: 'food', title: '饮食提醒', body: '记得打卡' },
    ])

    const { start, stop } = useCheckinReminder()
    start()
    await flush()

    expect(pendingReminders.value).toHaveLength(1)
    expect(ElNotification).toHaveBeenCalled()
    expect(notificationInstance.captured?.title).toBe('饮食提醒')

    notificationInstance.captured.onClose()
    expect(reminderApi.ackReminder).toHaveBeenCalledWith('log1')
    stop()
  })

  it('handles action buttons and snooze flow', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log4', tab: 'medication', body: '用药' },
    ])

    const { start, stop } = useCheckinReminder()
    start()
    await flush()

    const buttons = notificationInstance.captured.message.children[1].children
    buttons[0].props.onClick({ stopPropagation: vi.fn() })
    expect(reminderApi.clickReminder).toHaveBeenCalledWith('log4')
    const routerModule = await import('@/router')
    expect(routerModule.default.push).toHaveBeenCalledWith({ path: '/checkin-records/medication' })

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log5', tab: 'food', body: '稍后' },
    ])
    await useCheckinReminder().refresh()
    await flush()

    const snoozeButtons = notificationInstance.captured.message.children[1].children
    snoozeButtons[1].props.onClick({ stopPropagation: vi.fn() })
    expect(reminderApi.snoozeReminder).toHaveBeenCalledWith('log5', 15)
    stop()
  })

  it('uses browser notification when permitted and tab hidden', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const notification = await import('@/utils/notification')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log6', tab: 'glucose', title: '血糖', body: '测血糖' },
    ])

    const { start, stop } = useCheckinReminder()
    start()
    await flush()

    expect(notification.sendNotification).toHaveBeenCalledWith(expect.objectContaining({
      title: '血糖',
      body: '测血糖',
    }))
    const routerModule = await import('@/router')
    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log2', tab: 'exercise', body: '运动', banner_only: true },
    ])
    routerModule.default.currentRoute.value = { path: '/checkin-records/food', query: {} }
    await useCheckinReminder().refresh()
    await flush()
    expect(reminderApi.getPendingReminders).toHaveBeenCalled()
    stop()
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  it('acks reminder on close and handles poll failures', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log7', tab: 'food', body: '关闭测试' },
    ])

    const { start, stop } = useCheckinReminder()
    start()
    await flush()

    notificationInstance.captured.onClose()
    expect(reminderApi.ackReminder).toHaveBeenCalledWith('log7')
    stop()
  })

  it('skips duplicate reminders and ignores poll errors', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValue([
      { log_id: 'log8', tab: 'food', body: '重复' },
    ])

    const { start, stop } = useCheckinReminder()
    start()
    await flush()
    await useCheckinReminder().refresh()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalledTimes(1)

    reminderApi.getPendingReminders.mockRejectedValueOnce(new Error('poll failed'))
    await useCheckinReminder().refresh()
    stop()
  })

  it('does not start twice and snoozes even when api fails', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log9', tab: 'food', body: '稍后失败' },
    ])
    reminderApi.snoozeReminder.mockRejectedValueOnce(new Error('snooze failed'))

    const center = useCheckinReminder()
    center.start()
    center.start()
    await flush()

    const snoozeButton = notificationInstance.captured.message.children[1].children[1]
    snoozeButton.props.onClick({ stopPropagation: vi.fn() })
    expect(reminderApi.snoozeReminder).toHaveBeenCalledWith('log9', 15)
    center.stop()
  })

  it('continues when clickReminder fails before navigation', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const routerModule = await import('@/router')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log10', tab: 'medication', body: '点击失败' },
    ])
    reminderApi.clickReminder.mockRejectedValueOnce(new Error('click failed'))

    const { start, stop } = useCheckinReminder()
    start()
    await flush()

    const goButton = notificationInstance.captured.message.children[1].children[0]
    goButton.props.onClick({ stopPropagation: vi.fn() })
    await flush()
    expect(routerModule.default.push).toHaveBeenCalledWith({ path: '/checkin-records/medication' })
    stop()
  })

  it('covers reminder title defaults, snooze success and browser notification branches', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const notification = await import('@/utils/notification')
    const routerModule = await import('@/router')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { logId: 'log11', body: '默认标题', checkin_type: 2 },
    ])
    const { start, stop } = useCheckinReminder()
    start()
    await flush()
    notificationInstance.captured.onClose()
    expect(reminderApi.ackReminder).toHaveBeenCalledWith('log11')
    stop()

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log12', tab: 'food', body: '稍后成功' },
    ])
    reminderApi.snoozeReminder.mockResolvedValueOnce(null)
    const snoozeCenter = useCheckinReminder()
    snoozeCenter.start()
    await flush()
    const snoozeButton = notificationInstance.captured.message.children[1].children[1]
    snoozeButton.props.onClick({ stopPropagation: vi.fn() })
    await flush()
    expect(reminderApi.snoozeReminder).toHaveBeenCalledWith('log12', 15)
    snoozeCenter.stop()

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log13', tab: 'exercise' },
    ])
    const goCenter = useCheckinReminder()
    goCenter.start()
    await flush()
    const goButton = notificationInstance.captured.message.children[1].children[0]
    goButton.props.onClick({ stopPropagation: vi.fn() })
    await flush()
    notificationInstance.captured.onClose()
    expect(reminderApi.ackReminder).not.toHaveBeenCalledWith('log13')
    goCenter.stop()

    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log14', tab: 'glucose', checkinType: 4, title: '血糖提醒', body: '测血糖' },
    ])
    const browserCenter = useCheckinReminder()
    browserCenter.start()
    await flush()
    expect(notification.sendNotification).toHaveBeenCalledWith(expect.objectContaining({
      title: '血糖提醒',
      body: '测血糖',
    }))
    const browserOpts = notification.sendNotification.mock.calls.at(-1)[0]
    browserOpts.onClick()
    await flush()
    expect(routerModule.default.push).toHaveBeenCalledWith({ path: '/checkin-records/glucose' })
    browserCenter.stop()

    routerModule.default.currentRoute.value = { path: '/checkin-records/food', query: {} }
    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log15', tab: 'food', banner_only: true },
      { log_id: 'log16', tab: 'food', body: '页面内跳过' },
    ])
    const { ElNotification } = await import('element-plus')
    ElNotification.mockClear()
    const pageCenter = useCheckinReminder()
    pageCenter.start()
    await flush()
    expect(ElNotification).not.toHaveBeenCalled()
    pageCenter.stop()

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
    routerModule.default.currentRoute.value = { path: '/home', query: {} }
  })

  it('covers browser defaults, duplicate guards and goCheckin without log id', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const notification = await import('@/utils/notification')
    const routerModule = await import('@/router')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log-dup', tab: 'food', body: '1' },
      { log_id: 'log-dup', tab: 'food', body: '2' },
    ])
    const dupCenter = useCheckinReminder()
    dupCenter.start()
    await flush()
    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalledTimes(1)
    dupCenter.stop()

    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log-default', tab: 'food' },
    ])
    const browserCenter = useCheckinReminder()
    browserCenter.start()
    await flush()
    expect(notification.sendNotification).toHaveBeenCalledWith(expect.objectContaining({
      title: '打卡提醒',
      body: '尚未完成今日打卡',
    }))
    browserCenter.stop()

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { tab: 'medication', body: '无日志ID' },
    ])
    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
    const noLogCenter = useCheckinReminder()
    noLogCenter.start()
    await flush()
    const goButton = notificationInstance.captured.message.children[1].children[0]
    goButton.props.onClick({ stopPropagation: vi.fn() })
    await flush()
    expect(routerModule.default.push).toHaveBeenCalledWith({ path: '/checkin-records/medication' })
    noLogCenter.stop()
  })

  it('skips banner-only reminders outside checkin pages', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const routerModule = await import('@/router')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    routerModule.default.currentRoute.value = { path: '/home', query: {} }
    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log-banner', tab: 'food', banner_only: true, body: '横幅' },
    ])

    const { ElNotification } = await import('element-plus')
    ElNotification.mockClear()

    const { start, stop } = useCheckinReminder()
    start()
    await flush()
    expect(ElNotification).not.toHaveBeenCalled()
    stop()
  })

  it('covers browser duplicate guard and default type label title', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const notification = await import('@/utils/notification')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { log_id: 'log-browser-dup', tab: 'food', checkin_type_label: '饮食打卡', body: '1' },
      { log_id: 'log-browser-dup', tab: 'food', body: '2' },
    ])
    const dupCenter = useCheckinReminder()
    dupCenter.start()
    await flush()
    expect(notification.sendNotification).toHaveBeenCalledTimes(1)
    dupCenter.stop()

    reminderApi.getPendingReminders.mockResolvedValueOnce([
      { logId: 'log-label', checkin_type_label: '饮食打卡', body: '默认标题' },
    ])
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
    const labelCenter = useCheckinReminder()
    labelCenter.start()
    await flush()
    expect(notificationInstance.captured?.title).toBe('饮食打卡提醒')
    labelCenter.stop()
  })

  it('skips duplicate in-app reminders by log id', async () => {
    const reminderApi = await import('@/api/checkinReminder')
    const { useCheckinReminder } = await import('../useCheckinReminder')

    reminderApi.getPendingReminders.mockResolvedValue([
      { log_id: 'log-inapp-dup', tab: 'food', body: '1' },
    ])

    const { start, stop } = useCheckinReminder()
    start()
    await flush()
    await useCheckinReminder().refresh()
    await flush()

    const { ElNotification } = await import('element-plus')
    expect(ElNotification).toHaveBeenCalledTimes(1)
    stop()
  })

  it('covers internal browser and in-app notification guards', async () => {
    const notification = await import('@/utils/notification')
    const routerModule = await import('@/router')
    const { checkinReminderTestUtils } = await import('../useCheckinReminder')

    checkinReminderTestUtils.showInAppNotification({ log_id: 'log-guard', body: '1' })
    checkinReminderTestUtils.showInAppNotification({ log_id: 'log-guard', body: '2' })

    notification.isNotificationSupported.mockReturnValue(true)
    notification.getNotificationPermission.mockReturnValue('granted')
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    checkinReminderTestUtils.showBrowserNotification({ logId: 'browser-guard', tab: 'food' })
    checkinReminderTestUtils.showBrowserNotification({ logId: 'browser-guard', tab: 'food' })
    checkinReminderTestUtils.showBrowserNotification({ logId: 'browser-default-tab' })

    const opts = notification.sendNotification.mock.calls[0][0]
    opts.onClick()
    await flush()
    expect(routerModule.default.push).toHaveBeenCalledWith({ path: '/checkin-records/food' })

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })
})
