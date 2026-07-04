import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  put: mocks.put,
  post: mocks.post,
}))

function invokeGet(_url, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

function invokeWrite(_url, _payload, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockImplementation(invokeGet)
  mocks.put.mockImplementation(invokeWrite)
  mocks.post.mockImplementation(invokeWrite)
})

describe('checkin reminder and message api modules', () => {
  it('normalizes reminder rules, defaults and pending reminders', async () => {
    const api = await import('../checkinReminder')

    mocks.get.mockResolvedValueOnce([
      { checkinType: 2, remindTime: '17:30', enabled: true, sortOrder: 1 },
      { checkin_type: 9, remind_time: '09:00' },
    ])
    await expect(api.getReminderRules()).resolves.toEqual([
      expect.objectContaining({
        checkin_type: 2,
        checkin_type_label: '运动打卡',
        remind_time: '17:30',
        sort_order: 1,
        tab: 'exercise',
      }),
      expect.objectContaining({
        checkin_type: 9,
        checkin_type_label: '打卡',
        tab: 'food',
      }),
    ])

    mocks.get.mockResolvedValueOnce({ rules: [{ checkinType: 4, remindTime: '07:00' }] })
    await expect(api.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 4, checkin_type_label: '血糖打卡', tab: 'glucose' }),
    ])

    mocks.get.mockResolvedValueOnce([{ checkinType: 3, logId: 'log-1' }])
    await expect(api.getPendingReminders()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 3, log_id: 'log-1', tab: 'medication' }),
    ])
  })

  it('covers reminder mock fallbacks and write payloads', async () => {
    const api = await import('../checkinReminder')

    await expect(api.getReminderRules()).resolves.toEqual([])
    await expect(api.getPendingReminders()).resolves.toEqual([])
    await expect(api.getReminderDefaults()).resolves.toEqual(expect.arrayContaining([
      expect.objectContaining({ checkin_type: 1, tab: 'food' }),
      expect.objectContaining({ checkin_type: 3, tab: 'medication' }),
      expect.objectContaining({ checkin_type: 2, tab: 'exercise' }),
      expect.objectContaining({ checkin_type: 4, tab: 'glucose' }),
    ]))

    mocks.put.mockResolvedValueOnce([{ checkinType: 1, remindTime: '08:00', enabled: false }])
    await expect(api.saveReminderRules([
      { checkin_type: 1, remind_time: '08:00', enabled: false },
      { checkin_type: 2, remind_time: '17:30', enabled: true, sort_order: 3 },
    ])).resolves.toEqual([
      expect.objectContaining({ checkin_type: 1, enabled: false, tab: 'food' }),
    ])
    expect(mocks.put).toHaveBeenCalledWith('/checkin/reminders/rules', {
      rules: [
        { checkinType: 1, remindTime: '08:00', enabled: false, sortOrder: 0 },
        { checkinType: 2, remindTime: '17:30', enabled: true, sortOrder: 3 },
      ],
    }, expect.any(Object))

    mocks.put.mockImplementationOnce(invokeWrite)
    const saved = [
      { checkin_type: 4, remind_time: '10:00', enabled: true, sort_order: 2 },
    ]
    await expect(api.saveReminderRules(saved)).resolves.toEqual([
      expect.objectContaining({ checkin_type: 4, remind_time: '10:00', tab: 'glucose' }),
    ])

    await expect(api.ackReminder('l1')).resolves.toBeNull()
    await expect(api.snoozeReminder('l2')).resolves.toBeNull()
    expect(mocks.post).toHaveBeenLastCalledWith('/checkin/reminders/logs/l2/snooze', { minutes: 15 }, expect.any(Object))
    await expect(api.snoozeReminder('l3', 30)).resolves.toBeNull()
    expect(mocks.post).toHaveBeenLastCalledWith('/checkin/reminders/logs/l3/snooze', { minutes: 30 }, expect.any(Object))
    await expect(api.clickReminder('l4')).resolves.toBeNull()
  })

  it('passes message center api requests through request helpers', async () => {
    const api = await import('../message')
    mocks.get.mockResolvedValueOnce({ list: [] })
    await expect(api.getMessages({ unreadOnly: true })).resolves.toEqual({ list: [] })
    expect(mocks.get).toHaveBeenCalledWith('/user/messages', { unreadOnly: true })

    mocks.get.mockResolvedValueOnce({ unread_count: 2 })
    await expect(api.getUnreadMessageCount()).resolves.toEqual({ unread_count: 2 })
    expect(mocks.get).toHaveBeenLastCalledWith('/user/messages/unread-count')

    mocks.post.mockResolvedValueOnce({ ok: true })
    await expect(api.markMessageRead('m1')).resolves.toEqual({ ok: true })
    expect(mocks.post).toHaveBeenLastCalledWith('/user/messages/m1/read')

    mocks.post.mockResolvedValueOnce({ ok: true })
    await expect(api.markAllMessagesRead()).resolves.toEqual({ ok: true })
    expect(mocks.post).toHaveBeenLastCalledWith('/user/messages/read-all')

    mocks.post.mockResolvedValueOnce({ ok: true })
    await expect(api.markMessagesReadByBiz('consult_reply', 's1')).resolves.toEqual({ ok: true })
    expect(mocks.post).toHaveBeenLastCalledWith('/user/messages/read-by-biz', {
      messageType: 'consult_reply',
      bizId: 's1',
    })
  })
})
