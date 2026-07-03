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

function invokeMock(_url, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

function invokeWrite(_url, _data, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockImplementation(invokeMock)
  mocks.put.mockImplementation(invokeWrite)
  mocks.post.mockImplementation(invokeWrite)
})

describe('checkinReminder api', () => {
  it('loads and normalizes reminder rules', async () => {
    const api = await import('../checkinReminder')

    await expect(api.getReminderRules()).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce([
      { checkinType: 1, remindTime: '08:00', enabled: true, sortOrder: 0 },
    ])
    await expect(api.getReminderRules()).resolves.toEqual([
      expect.objectContaining({
        checkin_type: 1,
        remind_time: '08:00',
        checkin_type_label: '饮食打卡',
        tab: 'food',
      }),
    ])

    mocks.get.mockResolvedValueOnce({ not: 'array' })
    await expect(api.getReminderRules()).resolves.toEqual([])
  })

  it('saves reminder rules and loads defaults', async () => {
    const api = await import('../checkinReminder')
    const rules = [{ checkin_type: 2, remind_time: '17:30', enabled: true, sort_order: 0 }]

    await expect(api.saveReminderRules(rules)).resolves.toEqual([
      expect.objectContaining({ checkin_type: 2, tab: 'exercise' }),
    ])

    mocks.put.mockResolvedValueOnce([
      { checkinType: 4, remindTime: '07:00', enabled: false, sortOrder: 1 },
    ])
    await expect(api.saveReminderRules(rules)).resolves.toEqual([
      expect.objectContaining({ checkin_type: 4, tab: 'glucose', checkin_type_label: '血糖打卡' }),
    ])

    const defaults = await api.getReminderDefaults()
    expect(defaults.length).toBeGreaterThan(0)
    expect(defaults[0]).toMatchObject({ tab: expect.any(String) })

    mocks.get.mockResolvedValueOnce({ rules: [{ checkinType: 3, remindTime: '20:00' }] })
    await expect(api.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 3, tab: 'medication' }),
    ])

    mocks.get.mockResolvedValueOnce([{ checkinType: 1, remindTime: '09:00' }])
    await expect(api.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 1, tab: 'food' }),
    ])

    mocks.get.mockResolvedValueOnce({})
    await expect(api.getReminderDefaults()).resolves.toEqual([])
  })

  it('handles pending reminders and log actions', async () => {
    const api = await import('../checkinReminder')

    await expect(api.getPendingReminders()).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce([
      { checkinType: 2, remindTime: '18:00', logId: 'log1', title: '运动提醒' },
    ])
    await expect(api.getPendingReminders()).resolves.toEqual([
      expect.objectContaining({ tab: 'exercise', checkin_type_label: '运动打卡' }),
    ])

    mocks.get.mockResolvedValueOnce(null)
    await expect(api.getPendingReminders()).resolves.toEqual([])

    await expect(api.ackReminder('log1')).resolves.toBeNull()
    await expect(api.snoozeReminder('log1', 30)).resolves.toBeNull()
    await expect(api.clickReminder('log2')).resolves.toBeNull()
  })
})
