import { afterEach, describe, expect, it, vi } from 'vitest'
import { delay } from '../delay'

afterEach(() => {
  vi.useRealTimers()
})

describe('delay util', () => {
  it('resolves after the provided timeout and default timeout', async () => {
    vi.useFakeTimers()

    const short = vi.fn()
    const defaultDelay = vi.fn()
    delay(50).then(short)
    delay().then(defaultDelay)

    await vi.advanceTimersByTimeAsync(49)
    expect(short).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(short).toHaveBeenCalledTimes(1)
    expect(defaultDelay).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(250)
    expect(defaultDelay).toHaveBeenCalledTimes(1)
  })
})
