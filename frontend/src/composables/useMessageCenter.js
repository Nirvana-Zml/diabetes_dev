import { shallowRef, h } from 'vue'
import { ElNotification, ElButton } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import {
  getMessages,
  getUnreadMessageCount,
  markMessageRead,
  markMessagesReadByBiz,
} from '@/api/message'
import {
  isNotificationSupported,
  getNotificationPermission,
  sendNotification,
} from '@/utils/notification'

const POLL_INTERVAL_MS = 30_000

export const unreadCount = shallowRef(0)
export const messageList = shallowRef([])

const shownMessageIds = new Set()
let pollTimer = null
let running = false

function isMessageNotifyEnabled() {
  const ps = useUserStore().profile?.privacy_settings || useUserStore().profile?.privacySettings || {}
  if (ps.message_notify !== undefined) return ps.message_notify !== false
  if (ps.consult_notify !== undefined) return ps.consult_notify !== false
  return true
}

function buildRouteLocation(msg) {
  const path = msg.link_path || msg.linkPath || '/home'
  const query = msg.link_query || msg.linkQuery || {}
  return { path, query }
}

function shouldSuppressOnCurrentPage(msg) {
  const route = router.currentRoute.value
  const type = msg.message_type || msg.messageType
  const loc = buildRouteLocation(msg)

  if (type === 'consult_reply') {
    const onConsult = route.path.startsWith('/consultation')
    const sessionId = route.query.session_id || route.query.sessionId
    const bizId = msg.biz_id || msg.bizId
    if (onConsult && sessionId && bizId && String(sessionId) === String(bizId)) {
      return true
    }
    return false
  }

  if (route.path === loc.path) {
    const q = loc.query || {}
    const keys = Object.keys(q)
    if (keys.length === 0) return true
    return keys.every((k) => String(route.query[k] ?? '') === String(q[k] ?? ''))
  }
  return false
}

async function openMessage(msg) {
  const id = msg.message_id || msg.messageId
  if (id) {
    try {
      await markMessageRead(id)
    } catch {
      /* ignore */
    }
  }
  unreadCount.value = Math.max(0, unreadCount.value - 1)
  router.push(buildRouteLocation(msg))
}

function notificationTitle(msg) {
  const type = msg.message_type || msg.messageType
  const status = msg.status
  if (status === 'failed') {
    const map = {
      risk_assess: '风险评估失败',
      plan_generate: '方案生成失败',
      checkin_analysis: '打卡分析失败',
    }
    return map[type] || msg.title || '任务失败'
  }
  const map = {
    risk_assess: '风险评估已完成',
    plan_generate: '健康方案已就绪',
    consult_reply: '医生已回复',
    checkin_analysis: '打卡分析已更新',
  }
  return map[type] || msg.title || '消息通知'
}

function actionLabel(msg) {
  if (msg.status === 'failed') return '返回重试'
  if ((msg.message_type || msg.messageType) === 'consult_reply') return '进入会话'
  return '立即查看'
}

function showInAppNotification(msg) {
  const id = msg.message_id || msg.messageId
  if (!id || shownMessageIds.has(id)) return
  shownMessageIds.add(id)

  const isFailed = msg.status === 'failed'
  let dismissedByAction = false

  const notifyInstance = ElNotification({
    title: notificationTitle(msg),
    type: isFailed ? 'warning' : 'success',
    message: h('div', {}, [
      h('p', { style: 'margin:0 0 8px;line-height:1.5;' }, msg.summary || msg.title || ''),
      h(ElButton, {
        type: 'primary',
        size: 'small',
        onClick: (e) => {
          e.stopPropagation()
          dismissedByAction = true
          notifyInstance.close()
          openMessage(msg)
        },
      }, () => actionLabel(msg)),
    ]),
    duration: 8000,
    showClose: true,
    onClose: () => {
      if (dismissedByAction) return
    },
  })
}

function showBrowserNotification(msg) {
  const id = msg.message_id || msg.messageId
  if (!id || shownMessageIds.has(id)) return
  shownMessageIds.add(id)

  const loc = buildRouteLocation(msg)
  const query = new URLSearchParams(loc.query).toString()
  const url = query ? `${loc.path}?${query}` : loc.path

  sendNotification({
    title: notificationTitle(msg),
    body: msg.summary || msg.title || '',
    tag: `msg-center-${msg.message_type || msg.messageType}-${id}`,
    data: { url },
    onClick: () => openMessage(msg),
  })
}

async function autoMarkConsultOnPage(messages) {
  const route = router.currentRoute.value
  if (!route.path.startsWith('/consultation')) return
  const sessionId = route.query.session_id || route.query.sessionId
  if (!sessionId) return

  const hasConsultUnread = messages.some((m) => {
    const type = m.message_type || m.messageType
    const bizId = m.biz_id || m.bizId
    const read = m.is_read ?? m.isRead
    return type === 'consult_reply' && String(bizId) === String(sessionId) && !read
  })
  if (!hasConsultUnread) return

  try {
    await markMessagesReadByBiz('consult_reply', String(sessionId))
  } catch {
    /* ignore */
  }
}

async function pollOnce() {
  if (!running || !isMessageNotifyEnabled()) {
    unreadCount.value = 0
    return
  }
  try {
    const countRes = await getUnreadMessageCount()
    unreadCount.value = countRes?.unread_count ?? countRes?.unreadCount ?? 0

    if (unreadCount.value <= 0) return

    const listRes = await getMessages({ unreadOnly: true, limit: 10 })
    const list = listRes?.list || []
    messageList.value = list

    await autoMarkConsultOnPage(list)
    if (unreadCount.value > 0) {
      const refreshed = await getUnreadMessageCount()
      unreadCount.value = refreshed?.unread_count ?? refreshed?.unreadCount ?? 0
    }

    const canBrowserPush = isNotificationSupported()
      && getNotificationPermission() === 'granted'
      && document.visibilityState === 'hidden'

    for (const msg of list) {
      const id = msg.message_id || msg.messageId
      if (!id || shownMessageIds.has(id)) continue
      if (msg.is_read || msg.isRead) continue
      if (shouldSuppressOnCurrentPage(msg)) {
        shownMessageIds.add(id)
        continue
      }
      if (canBrowserPush) {
        showBrowserNotification(msg)
      } else if (document.visibilityState === 'visible') {
        showInAppNotification(msg)
      }
    }
  } catch {
    /* 轮询失败静默 */
  }
}

export function useMessageCenter() {
  async function loadList() {
    if (!isMessageNotifyEnabled()) {
      messageList.value = []
      unreadCount.value = 0
      return { list: [], unread_count: 0 }
    }
    const res = await getMessages({ limit: 20 })
    messageList.value = res?.list || []
    unreadCount.value = res?.unread_count ?? res?.unreadCount ?? 0
    return res
  }

  function start() {
    if (running) return
    running = true
    shownMessageIds.clear()
    pollOnce()
    pollTimer = window.setInterval(pollOnce, POLL_INTERVAL_MS)
    document.addEventListener('visibilitychange', onVisible)
  }

  function stop() {
    running = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    document.removeEventListener('visibilitychange', onVisible)
    unreadCount.value = 0
    messageList.value = []
    shownMessageIds.clear()
  }

  function onVisible() {
    if (document.visibilityState === 'visible') {
      pollOnce()
    }
  }

  function refresh() {
    return pollOnce()
  }

  async function markConsultSessionRead(sessionId) {
    if (!sessionId) return
    try {
      await markMessagesReadByBiz('consult_reply', String(sessionId))
      await pollOnce()
    } catch {
      /* ignore */
    }
  }

  return {
    start,
    stop,
    refresh,
    loadList,
    markConsultSessionRead,
    unreadCount,
    messageList,
  }
}
