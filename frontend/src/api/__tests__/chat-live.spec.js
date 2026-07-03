import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchBackendSSE: vi.fn(),
}))

vi.mock('@/config', () => ({
  USE_MOCK: false,
}))

vi.mock('@/api/dify', () => ({
  difyQaChat: vi.fn(),
}))

vi.mock('@/utils/sse', () => ({
  fetchBackendSSE: mocks.fetchBackendSSE,
}))

vi.mock('@/utils/request', () => ({
  post: vi.fn(),
}))

vi.mock('@/utils/delay', () => ({
  delay: vi.fn(() => Promise.resolve()),
}))

vi.mock('@/composables/useVoiceInput', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    normalizeVoiceBlob: vi.fn(async (blob) => new Blob([blob], { type: 'audio/wav' })),
    voiceBlobFilename: actual.voiceBlobFilename,
  }
})

beforeEach(() => {
  vi.clearAllMocks()
})

describe('chat api backend SSE mode', () => {
  it('maps backend SSE message and end events', async () => {
    const { chatQA } = await import('../chat')
    const onChunk = vi.fn()
    const onEnd = vi.fn()

    mocks.fetchBackendSSE.mockImplementation(async (_url, options) => {
      options.onEvent({ event: 'message', data: { content: '回答', conversationId: 'c1' } })
      options.onEvent({ event: 'message', data: { type: 'text', content: '继续', conversation_id: 'c1' } })
      options.onEvent({ event: 'message_end', data: { metadata: { sources: ['指南'] }, conversation_id: 'c1' } })
    })

    await chatQA('血糖怎么控制', { conversationId: 'c1', onChunk, onEnd })

    expect(mocks.fetchBackendSSE).toHaveBeenCalledWith('/chat/qa', expect.objectContaining({
      body: { query: '血糖怎么控制', conversationId: 'c1' },
      onEvent: expect.any(Function),
    }))
    expect(onChunk).toHaveBeenCalledWith({ type: 'text', content: '回答', conversation_id: 'c1' })
    expect(onChunk).toHaveBeenCalledWith({ type: 'text', content: '继续', conversation_id: 'c1' })
    expect(onEnd).toHaveBeenCalledWith({ type: 'end', conversation_id: 'c1', metadata: { sources: ['指南'] } })
  })

  it('throws backend SSE error events', async () => {
    const { chatQA } = await import('../chat')
    mocks.fetchBackendSSE.mockImplementation(async (_url, options) => {
      options.onEvent({ event: 'error', data: { message: '问答失败' } })
    })

    await expect(chatQA('问题')).rejects.toThrow('问答失败')
  })

  it('throws when payload type is error', async () => {
    const { chatQA } = await import('../chat')
    mocks.fetchBackendSSE.mockImplementation(async (_url, options) => {
      options.onEvent({ event: 'message', data: { type: 'error', message: '服务异常' } })
    })

    await expect(chatQA('问题')).rejects.toThrow('服务异常')
  })

  it('throws with default message when backend error payload is empty', async () => {
    const { chatQA } = await import('../chat')
    mocks.fetchBackendSSE.mockImplementation(async (_url, options) => {
      options.onEvent({ event: 'error', data: {} })
    })

    await expect(chatQA('问题')).rejects.toThrow('问答服务失败')
  })

  it('handles end events emitted only through payload type', async () => {
    const { chatQA } = await import('../chat')
    const onEnd = vi.fn()
    mocks.fetchBackendSSE.mockImplementation(async (_url, options) => {
      options.onEvent({ event: 'done', data: { type: 'end', conversationId: 'c9' } })
    })

    await chatQA('结束', { onEnd })
    expect(onEnd).toHaveBeenCalledWith({ type: 'end', conversation_id: 'c9', metadata: undefined })
  })
})

describe('voiceToText api', () => {
  it('posts multipart audio to backend voice endpoint', async () => {
    const { post } = await import('@/utils/request')
    const { voiceToText } = await import('../chat')
    const blob = new Blob(['audio'], { type: 'audio/webm' })
    post.mockResolvedValue({ text: '你好', language: 'zh-CN' })

    const result = await voiceToText(blob, { language: 'zh-CN' })

    expect(post).toHaveBeenCalledWith('/chat/voice', expect.any(FormData), { timeout: 120000 })
    expect(result).toEqual({ text: '你好', language: 'zh-CN' })
  })
})
