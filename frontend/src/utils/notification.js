/** 浏览器 Notification API 封装（含兼容性判断） */

export function isNotificationSupported() {
  return typeof Notification !== 'undefined' && typeof window.Notification !== 'undefined'
}

export function getNotificationPermission() {
  if (!isNotificationSupported()) return 'unsupported'
  return Notification.permission
}

/**
 * 请求或检查通知权限；需在用户手势（点击）中调用 requestPermission
 * @returns {Promise<'granted'|'denied'|'default'|'unsupported'>}
 */
export function requestNotificationPermission() {
  if (!isNotificationSupported()) {
    return Promise.resolve('unsupported')
  }
  const permissionNow = Notification.permission
  if (permissionNow === 'granted' || permissionNow === 'denied') {
    return Promise.resolve(permissionNow)
  }
  return Notification.requestPermission()
}

/**
 * 发送系统级通知
 * @param {object} options
 * @param {string} options.title
 * @param {string} options.body
 * @param {string} [options.tag]
 * @param {string} [options.icon]
 * @param {object} [options.data]
 * @param {() => void} [options.onClick]
 */
export function sendNotification({ title, body, tag, icon, data, onClick }) {
  if (!isNotificationSupported() || Notification.permission !== 'granted') {
    return null
  }
  const n = new Notification(title, {
    body,
    tag: tag || `notify-${Date.now()}`,
    icon: icon || '/favicon.ico',
    requireInteraction: true,
    renotify: true,
    data: data || {},
  })
  n.onclick = () => {
    window.focus()
    onClick?.()
    n.close()
  }
  return n
}
