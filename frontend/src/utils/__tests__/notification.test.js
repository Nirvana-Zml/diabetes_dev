import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getNotificationPermission,
  isNotificationSupported,
  requestNotificationPermission,
  sendNotification,
} from '../notification'

describe('notification utils', () => {
  const originalNotification = global.Notification

  afterEach(() => {
    if (originalNotification) {
      global.Notification = originalNotification
    } else {
      delete global.Notification
    }
    vi.restoreAllMocks()
  })

  it('detects unsupported environments', () => {
    delete global.Notification
    expect(isNotificationSupported()).toBe(false)
    expect(getNotificationPermission()).toBe('unsupported')
    return expect(requestNotificationPermission()).resolves.toBe('unsupported')
  })

  it('returns current permission without re-requesting', async () => {
    global.Notification = vi.fn()
    Object.defineProperty(global.Notification, 'permission', { value: 'granted', configurable: true })

    expect(getNotificationPermission()).toBe('granted')
    await expect(requestNotificationPermission()).resolves.toBe('granted')

    Object.defineProperty(global.Notification, 'permission', { value: 'denied', configurable: true })
    await expect(requestNotificationPermission()).resolves.toBe('denied')
  })

  it('requests permission when default', async () => {
    global.Notification = vi.fn()
    Object.defineProperty(global.Notification, 'permission', { value: 'default', configurable: true })
    global.Notification.requestPermission = vi.fn(async () => 'granted')

    await expect(requestNotificationPermission()).resolves.toBe('granted')
    expect(global.Notification.requestPermission).toHaveBeenCalled()
  })

  it('sends browser notification when granted', () => {
    const close = vi.fn()
    const onClick = vi.fn()
    global.Notification = vi.fn(function MockNotification() {
      this.close = close
    })
    Object.defineProperty(global.Notification, 'permission', { value: 'granted', configurable: true })
    vi.spyOn(window, 'focus').mockImplementation(() => {})

    const result = sendNotification({
      title: '提醒',
      body: '内容',
      tag: 't1',
      icon: '/icon.png',
      data: { url: '/home' },
      onClick,
    })

    expect(result).toBeTruthy()
    expect(global.Notification).toHaveBeenCalledWith('提醒', expect.objectContaining({
      body: '内容',
      tag: 't1',
      icon: '/icon.png',
    }))

    result.onclick()
    expect(window.focus).toHaveBeenCalled()
    expect(onClick).toHaveBeenCalled()
    expect(close).toHaveBeenCalled()

    Object.defineProperty(global.Notification, 'permission', { value: 'denied', configurable: true })
    expect(sendNotification({ title: 'x', body: 'y' })).toBeNull()
  })
})
