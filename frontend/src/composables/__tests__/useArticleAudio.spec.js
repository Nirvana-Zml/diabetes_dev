import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  getArticleAudio: vi.fn(),
}))

vi.mock('@/api/article', () => ({
  getArticleAudio: mocks.getArticleAudio,
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() },
}))

import { ElMessage } from 'element-plus'
import { useArticleAudio } from '../useArticleAudio'

class MockAudio {
  static lastInstance = null

  constructor(url) {
    this.url = url
    this.src = url
    this._handlers = {}
    MockAudio.lastInstance = this
  }

  addEventListener(event, handler) {
    this._handlers[event] = handler
  }

  pause = vi.fn()

  play = vi.fn(async () => {
    this._handlers.play?.()
  })

  emit(event) {
    this._handlers[event]?.()
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  MockAudio.lastInstance = null
  vi.stubGlobal('Audio', MockAudio)
})

describe('useArticleAudio', () => {
  it('ignores toggle without article id', async () => {
    const { toggle, playing } = useArticleAudio()
    await toggle('')
    expect(playing.value).toBe(false)
    expect(mocks.getArticleAudio).not.toHaveBeenCalled()
  })

  it('pauses when already playing', async () => {
    mocks.getArticleAudio.mockResolvedValueOnce({ audio_url: 'http://audio.test/a.wav' })
    const { toggle, playing } = useArticleAudio()
    await toggle('a1')
    expect(playing.value).toBe(true)
    await toggle('a1')
    expect(playing.value).toBe(false)
    expect(MockAudio.lastInstance.pause).toHaveBeenCalled()
  })

  it('resumes cached audio without refetching', async () => {
    mocks.getArticleAudio.mockResolvedValueOnce({ audio_url: 'http://audio.test/a.wav' })
    const { toggle, playing } = useArticleAudio()
    await toggle('a1')
    MockAudio.lastInstance.emit('pause')
    expect(playing.value).toBe(false)
    mocks.getArticleAudio.mockClear()
    await toggle('a1')
    expect(mocks.getArticleAudio).not.toHaveBeenCalled()
    expect(playing.value).toBe(true)
  })

  it('loads audio and handles ended event', async () => {
    mocks.getArticleAudio.mockResolvedValueOnce({ audioUrl: 'http://audio.test/b.wav' })
    const { toggle, playing, loading } = useArticleAudio()
    await toggle('a2')
    expect(loading.value).toBe(false)
    expect(playing.value).toBe(true)
    MockAudio.lastInstance.emit('ended')
    expect(playing.value).toBe(false)
  })

  it('shows timeout message on synthesis timeout', async () => {
    mocks.getArticleAudio.mockRejectedValueOnce(new Error('request timeout'))
    const { toggle, playing } = useArticleAudio()
    await toggle('a3')
    expect(ElMessage.error).toHaveBeenCalledWith('语音合成时间较长，请保持页面打开并重试（长文约需 1～3 分钟）')
    expect(playing.value).toBe(false)
  })

  it('shows generic error when audio url missing', async () => {
    mocks.getArticleAudio.mockResolvedValueOnce({ article_id: 'a4' })
    const { toggle } = useArticleAudio()
    await toggle('a4')
    expect(ElMessage.error).toHaveBeenCalledWith('未获取到朗读音频')
  })

  it('shows fallback error message when rejection has no message', async () => {
    mocks.getArticleAudio.mockRejectedValueOnce({})
    const { toggle } = useArticleAudio()
    await toggle('a5')
    expect(ElMessage.error).toHaveBeenCalledWith('语音朗读加载失败')
  })

  it('stop clears audio state', async () => {
    mocks.getArticleAudio.mockResolvedValueOnce({ audio_url: 'http://audio.test/c.wav' })
    const { toggle, stop, playing } = useArticleAudio()
    await toggle('a5')
    stop()
    expect(playing.value).toBe(false)
  })

  it('cleans up audio on unmount', async () => {
    mocks.getArticleAudio.mockResolvedValueOnce({ audio_url: 'http://audio.test/d.wav' })
    const { toggle, stop } = useArticleAudio()
    await toggle('a6')
    const instance = MockAudio.lastInstance
    stop()
    await nextTick()
    expect(instance.pause).toHaveBeenCalled()
  })
})
