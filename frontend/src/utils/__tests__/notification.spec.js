import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  getNotificationPermission,
  isNotificationSupported,
  requestNotificationPermission,
  sendNotification,
} from '../notification'

const originalNotification = globalThis.Notification
const originalWindowNotification = window.Notification

function setNotificationStub(stub) {
  Object.defineProperty(globalThis, 'Notification', {
    configurable: true,
    writable: true,
    value: stub,
  })
  Object.defineProperty(window, 'Notification', {
    configurable: true,
    writable: true,
    value: stub,
  })
}

afterEach(() => {
  setNotificationStub(originalNotification)
  Object.defineProperty(window, 'Notification', {
    configurable: true,
    writable: true,
    value: originalWindowNotification,
  })
  vi.restoreAllMocks()
})

describe('notification utility', () => {
  it('reports unsupported browser notification api', async () => {
    setNotificationStub(undefined)

    expect(isNotificationSupported()).toBe(false)
    expect(getNotificationPermission()).toBe('unsupported')
    await expect(requestNotificationPermission()).resolves.toBe('unsupported')
    expect(sendNotification({ title: '标题' })).toBeNull()
  })

  it('returns existing granted or denied permission without requesting again', async () => {
    const requestPermission = vi.fn()
    setNotificationStub({ permission: 'granted', requestPermission })
    expect(isNotificationSupported()).toBe(true)
    expect(getNotificationPermission()).toBe('granted')
    await expect(requestNotificationPermission()).resolves.toBe('granted')

    setNotificationStub({ permission: 'denied', requestPermission })
    await expect(requestNotificationPermission()).resolves.toBe('denied')
    expect(requestPermission).not.toHaveBeenCalled()
  })

  it('requests permission while current permission is default', async () => {
    const requestPermission = vi.fn(async () => 'granted')
    setNotificationStub({ permission: 'default', requestPermission })

    await expect(requestNotificationPermission()).resolves.toBe('granted')
    expect(requestPermission).toHaveBeenCalled()
  })

  it('sends a notification and wires click behavior', () => {
    const close = vi.fn()
    const onClick = vi.fn()
    const focus = vi.spyOn(window, 'focus').mockImplementation(() => {})
    const notificationInstances = []
    function NotificationMock(title, options) {
      this.title = title
      this.options = options
      this.close = close
      this.onclick = null
      notificationInstances.push(this)
    }
    NotificationMock.permission = 'granted'
    setNotificationStub(NotificationMock)

    const notification = sendNotification({
      title: '消息',
      body: '内容',
      tag: 'tag-1',
      icon: '/icon.png',
      data: { url: '/home' },
      onClick,
    })

    expect(notification).toBe(notificationInstances[0])
    expect(notification.title).toBe('消息')
    expect(notification.options).toEqual({
      body: '内容',
      tag: 'tag-1',
      icon: '/icon.png',
      requireInteraction: true,
      renotify: true,
      data: { url: '/home' },
    })

    notification.onclick()
    expect(focus).toHaveBeenCalled()
    expect(onClick).toHaveBeenCalled()
    expect(close).toHaveBeenCalled()
  })

  it('uses fallback tag, icon and data only when notifications are granted', () => {
    vi.spyOn(Date, 'now').mockReturnValue(123)
    const notificationInstances = []
    function NotificationMock(title, options) {
      this.title = title
      this.options = options
      this.close = vi.fn()
      notificationInstances.push(this)
    }
    NotificationMock.permission = 'default'
    setNotificationStub(NotificationMock)
    expect(sendNotification({ title: '忽略' })).toBeNull()

    NotificationMock.permission = 'granted'
    const notification = sendNotification({ title: '默认值' })
    expect(notification.options).toMatchObject({
      tag: 'notify-123',
      icon: '/favicon.ico',
      data: {},
    })
  })
})
