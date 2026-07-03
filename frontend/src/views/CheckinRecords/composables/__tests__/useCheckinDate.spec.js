import { beforeEach, describe, expect, it, vi } from 'vitest'
import dayjs from 'dayjs'

vi.mock('../../checkin/utils', () => ({
  buildDateDisplay: vi.fn((date) => `显示 ${date}`),
}))

describe('useCheckinDate', () => {
  beforeEach(async () => {
    vi.resetModules()
    const { buildDateDisplay } = await import('../../checkin/utils')
    buildDateDisplay.mockImplementation((date) => `显示 ${date}`)
  })

  it('tracks today state and formatted display', async () => {
    const today = dayjs().format('YYYY-MM-DD')
    const { useCheckinDate } = await import('../useCheckinDate')
    const { checkinDate, isToday, dateDisplay } = useCheckinDate()

    expect(checkinDate.value).toBe(today)
    expect(isToday.value).toBe(true)
    expect(dateDisplay.value).toBe(`显示 ${today}`)
  })

  it('changes date by delta and ignores empty setCheckinDate', async () => {
    const { useCheckinDate } = await import('../useCheckinDate')
    const { checkinDate, isToday, changeDate, setCheckinDate } = useCheckinDate()
    const today = dayjs().format('YYYY-MM-DD')

    changeDate(-1)
    const yesterday = dayjs(today).subtract(1, 'day').format('YYYY-MM-DD')
    expect(checkinDate.value).toBe(yesterday)
    expect(isToday.value).toBe(false)

    setCheckinDate('')
    expect(checkinDate.value).toBe(yesterday)

    setCheckinDate(today)
    expect(checkinDate.value).toBe(today)
    expect(isToday.value).toBe(true)

    changeDate(1)
    const tomorrow = dayjs(today).add(1, 'day').format('YYYY-MM-DD')
    expect(checkinDate.value).toBe(tomorrow)
  })
})
