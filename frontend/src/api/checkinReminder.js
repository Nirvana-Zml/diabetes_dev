import { get, put, post } from '@/utils/request'
import { toSnakeCase, toCamelCase } from '@/utils/normalize'
import dayjs from 'dayjs'

const TYPE_LABELS = { 1: '饮食', 2: '运动', 3: '用药', 4: '血糖' }
const TAB_MAP = { 1: 'food', 2: 'exercise', 3: 'medication', 4: 'glucose' }

const mockDefaults = {
  rules: [
    { checkin_type: 1, checkin_type_label: '饮食打卡', remind_time: '08:00', enabled: true, sort_order: 0 },
    { checkin_type: 1, checkin_type_label: '饮食打卡', remind_time: '12:00', enabled: true, sort_order: 1 },
    { checkin_type: 1, checkin_type_label: '饮食打卡', remind_time: '18:00', enabled: true, sort_order: 2 },
    { checkin_type: 3, checkin_type_label: '用药打卡', remind_time: '08:00', enabled: true, sort_order: 0 },
    { checkin_type: 3, checkin_type_label: '用药打卡', remind_time: '20:00', enabled: true, sort_order: 1 },
    { checkin_type: 2, checkin_type_label: '运动打卡', remind_time: '17:30', enabled: true, sort_order: 0 },
    { checkin_type: 4, checkin_type_label: '血糖打卡', remind_time: '07:00', enabled: true, sort_order: 0 },
    { checkin_type: 4, checkin_type_label: '血糖打卡', remind_time: '10:00', enabled: true, sort_order: 1 },
  ],
}

function normalizeRule(item) {
  const r = toSnakeCase(item)
  return {
    ...r,
    checkin_type_label: r.checkin_type_label || `${TYPE_LABELS[r.checkin_type] || ''}打卡`,
    tab: TAB_MAP[r.checkin_type] || 'food',
  }
}

/** GET /checkin/reminders/rules */
export function getReminderRules() {
  return get('/checkin/reminders/rules', {
    mockFn: async () => [],
  }).then((data) => (Array.isArray(data) ? data.map(normalizeRule) : []))
}

/** PUT /checkin/reminders/rules */
export function saveReminderRules(rules) {
  const payload = {
    rules: rules.map((r) => ({
      checkinType: r.checkin_type,
      remindTime: r.remind_time,
      enabled: r.enabled !== false,
      sortOrder: r.sort_order ?? 0,
    })),
  }
  return put('/checkin/reminders/rules', payload, {
    mockFn: async () => rules,
  }).then((data) => (Array.isArray(data) ? data.map(normalizeRule) : rules))
}

/** GET /checkin/reminders/defaults */
export function getReminderDefaults() {
  return get('/checkin/reminders/defaults', {
    mockFn: async () => mockDefaults,
  }).then((data) => {
    const rules = Array.isArray(data) ? data : (data?.rules ?? [])
    return rules.map(normalizeRule)
  })
}

/** GET /checkin/reminders/pending */
export function getPendingReminders() {
  return get('/checkin/reminders/pending', {
    mockFn: async () => [],
  }).then((data) => (Array.isArray(data) ? data.map(normalizeRule) : []))
}

/** POST /checkin/reminders/logs/{logId}/ack */
export function ackReminder(logId) {
  return post(`/checkin/reminders/logs/${logId}/ack`, {}, {
    mockFn: async () => null,
  })
}

/** POST /checkin/reminders/logs/{logId}/snooze */
export function snoozeReminder(logId, minutes = 15) {
  return post(`/checkin/reminders/logs/${logId}/snooze`, { minutes }, {
    mockFn: async () => null,
  })
}

/** POST /checkin/reminders/logs/{logId}/click */
export function clickReminder(logId) {
  return post(`/checkin/reminders/logs/${logId}/click`, {}, {
    mockFn: async () => null,
  })
}
