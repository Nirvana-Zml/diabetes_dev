import { shallowRef } from 'vue'
import router from '@/router'
import { showInAppNotification as openInAppNotification } from '@/utils/inAppNotification'
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
  return { path: `/checkin-records/${tab}` }
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

  const notifyInstance = openInAppNotification({
    title: item.title || `${item.checkin_type_label || '打卡'}提醒`,
    summary: item.body || '尚未完成今日打卡',
    actions: [
      {
        label: '去打卡',
        type: 'primary',
        onClick: (e) => {
          e.stopPropagation()
          dismissedByAction = true
          notifyInstance.close()
          goCheckin(tab, logId)
        },
      },
      {
        label: '稍后提醒',
        onClick: (e) => {
          e.stopPropagation()
          dismissedByAction = true
          notifyInstance.close()
          snoozeReminder(logId, 15).then(() => shownLogIds.delete(logId)).catch(() => {})
        },
      },
    ],
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

    const onCheckinPage = router.currentRoute.value.path.startsWith('/checkin-records')
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

/** @internal 供单元测试覆盖通知分支 */
export const checkinReminderTestUtils = {
  showInAppNotification,
  showBrowserNotification,
}
