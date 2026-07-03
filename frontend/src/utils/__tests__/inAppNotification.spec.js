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

import { showInAppNotification, isMobileViewport, getNotificationActionButtons } from '../inAppNotification'

describe('inAppNotification', () => {
  beforeEach(() => {
    notificationInstance.captured = null
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  })

  it('uses top-right layout on desktop', () => {
    showInAppNotification({
      title: '测试通知',
      summary: '正文内容',
      actions: [{ label: '查看', type: 'primary', onClick: vi.fn() }],
    })

    expect(notificationInstance.captured.position).toBe('top-right')
    expect(notificationInstance.captured.customClass).toBe('in-app-notification')
    const buttons = getNotificationActionButtons(notificationInstance.captured.message)
    expect(buttons[0].children.default()).toBe('查看')
  })

  it('uses top compact card layout on mobile', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    expect(isMobileViewport()).toBe(true)

    showInAppNotification({
      title: '健康指标需关注',
      summary: '空腹血糖偏高，建议加强监测',
      actions: [{ label: '立即查看', type: 'primary', onClick: vi.fn() }],
    })

    expect(notificationInstance.captured.position).toBe('top-right')
    expect(notificationInstance.captured.customClass).toContain('in-app-notification--mobile')
    expect(notificationInstance.captured.message.children[1].props.class).toBe('in-app-notification__actions')
  })

  it('returns empty list from getNotificationActionButtons when message is missing', () => {
    expect(getNotificationActionButtons(null)).toEqual([])
    expect(getNotificationActionButtons(undefined)).toEqual([])
  })

  it('forwards onClose and custom duration', () => {
    const onClose = vi.fn()
    showInAppNotification({
      title: '提醒',
      summary: '内容',
      duration: 3000,
      onClose,
    })
    expect(notificationInstance.captured.duration).toBe(3000)
    notificationInstance.captured.onClose()
    expect(onClose).toHaveBeenCalled()
  })
})
