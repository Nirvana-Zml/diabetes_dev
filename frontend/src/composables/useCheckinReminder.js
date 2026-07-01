import { shallowRef, h } from 'vue'
import { ElNotification, ElButton } from 'element-plus'
import router from '@/router'
import {
  getPendingReminders,
  ackReminder,
  snoozeReminder,
  clickReminder,
} from '@/api/checkinReminder'
import {
  isNotificationSupported,
  getNotificationPermission,
  sendNotification,
} from '@/utils/notification'

const POLL_INTERVAL_MS = 60_000

/** 供打卡页读取的待提醒列表 */
export const pendingReminders = shallowRef([])

const shownLogIds = new Set()
let pollTimer = null
let running = false

function tabPath(tab) {
  return { path: '/checkin-records', query: { tab } }
}

async function goCheckin(tab, logId) {
  if (logId) {
    try {
      await clickReminder(logId)
    } catch {
      /* ignore */
    }
  }
  router.push(tabPath(tab))
}

function showInAppNotification(item) {
  const logId = item.log_id || item.logId
  if (shownLogIds.has(logId)) return
  shownLogIds.add(logId)

  const tab = item.tab || 'food'
  let dismissedByAction = false

  const notifyInstance = ElNotification({
    title: item.title || `${item.checkin_type_label || '打卡'}提醒`,
    message: h('div', {}, [
      h('p', { style: 'margin:0 0 8px;line-height:1.5;' }, item.body || '尚未完成今日打卡'),
      h('div', { style: 'display:flex;gap:8px;' }, [
        h(ElButton, {
          type: 'primary',
          size: 'small',
          onClick: (e) => {
            e.stopPropagation()
            dismissedByAction = true
            notifyInstance.close()
            goCheckin(tab, logId)
          },
        }, () => '去打卡'),
        h(ElButton, {
          size: 'small',
          onClick: (e) => {
            e.stopPropagation()
            dismissedByAction = true
            notifyInstance.close()
            snoozeReminder(logId, 15).then(() => shownLogIds.delete(logId)).catch(() => {})
          },
        }, () => '稍后提醒'),
      ]),
    ]),
    duration: 8000,
    showClose: true,
    onClose: () => {
      if (dismissedByAction) return
      ackReminder(logId).catch(() => {})
    },
  })
}

function showBrowserNotification(item) {
  const logId = item.log_id || item.logId
  if (shownLogIds.has(logId)) return
  shownLogIds.add(logId)

  const tab = item.tab || 'food'
  const date = new Date().toISOString().slice(0, 10)
  sendNotification({
    title: item.title || '打卡提醒',
    body: item.body || '尚未完成今日打卡',
    tag: `checkin-reminder-${item.checkin_type || item.checkinType}-${date}`,
    onClick: () => goCheckin(tab, logId),
  })
}

async function pollOnce() {
  if (!running) return
  try {
    const list = await getPendingReminders()
    pendingReminders.value = list

    const onCheckinPage = router.currentRoute.value.path === '/checkin-records'
    const canBrowserPush = isNotificationSupported()
      && getNotificationPermission() === 'granted'
      && document.visibilityState === 'hidden'

    for (const item of list) {
      const logId = item.log_id || item.logId
      if (shownLogIds.has(logId)) continue

      if (onCheckinPage) continue

      if (item.banner_only) continue

      if (canBrowserPush) {
        showBrowserNotification(item)
      } else {
        showInAppNotification(item)
      }
    }
  } catch {
    /* 轮询失败静默 */
  }
}

export function useCheckinReminder() {
  function start() {
    if (running) return
    running = true
    shownLogIds.clear()
    pollOnce()
    pollTimer = window.setInterval(pollOnce, POLL_INTERVAL_MS)
  }

  function stop() {
    running = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    pendingReminders.value = []
    shownLogIds.clear()
  }

  function refresh() {
    return pollOnce()
  }

  return { start, stop, refresh, pendingReminders }
}
