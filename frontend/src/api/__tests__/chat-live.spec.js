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

  it('omits conversation id when starting a new backend chat', async () => {
    const { chatQA } = await import('../chat')
    mocks.fetchBackendSSE.mockResolvedValue()

    await chatQA('新问题')

    expect(mocks.fetchBackendSSE).toHaveBeenCalledWith('/chat/qa', expect.objectContaining({
      body: { query: '新问题' },
    }))
  })
})
