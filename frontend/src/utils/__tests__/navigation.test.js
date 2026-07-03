import { beforeEach, describe, expect, it, vi } from 'vitest'

const replace = vi.fn()
const back = vi.fn()

vi.mock('@/router', () => ({
  default: {
    replace,
    back,
  },
}))

describe('safeBack', () => {
  beforeEach(() => {
    replace.mockClear()
    back.mockClear()
    vi.stubGlobal('history', {
      length: 2,
      state: { back: '/home' },
    })
  })

  it('replaces with fallback when previous page is login', async () => {
    history.state = { back: '/login?redirect=/consultation/chat' }
    const { safeBack } = await import('../navigation')
    safeBack('/home')
    expect(replace).toHaveBeenCalledWith('/home')
    expect(back).not.toHaveBeenCalled()
  })

  it('uses history back for normal navigation', async () => {
    history.state = { back: '/consultation' }
    const { safeBack } = await import('../navigation')
    safeBack('/home')
    expect(back).toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
  })

  it('replaces with fallback when history is empty', async () => {
    history.length = 1
    history.state = {}
    const { safeBack } = await import('../navigation')
    safeBack('/home')
    expect(replace).toHaveBeenCalledWith('/home')
  })
})
