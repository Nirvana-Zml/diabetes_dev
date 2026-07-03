import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  mockSSEStream: vi.fn(),
}))

vi.mock('@/config', () => ({
  USE_MOCK: true,
  DIFY_WORKFLOWS: {
    QA: 'qa-url',
    CONSULTATION: 'consult-url',
    RISK: 'risk-url',
    PLAN: 'plan-url',
    ARTICLE_DRAFT: 'article-url',
  },
}))

vi.mock('@/utils/delay', () => ({
  delay: vi.fn(async () => {}),
}))

vi.mock('@/utils/sse', () => ({
  mockSSEStream: mocks.mockSSEStream,
}))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.mockSSEStream.mockImplementation(async (chunks, onChunk, onEnd) => {
    for (const c of chunks) onChunk?.({ type: 'text', content: c })
    onEnd?.({ type: 'end' })
  })
})

describe('dify branch coverage', () => {
  it('invokes qa stream callbacks with conversation id', async () => {
    const onChunk = vi.fn()
    const onEnd = vi.fn()
    const dify = await import('../dify')

    await dify.difyQaChat('糖尿病饮食', { onChunk, onEnd, conversationId: 'conv-live' })

    expect(mocks.mockSSEStream).toHaveBeenCalled()
    expect(onChunk).toHaveBeenCalled()
    expect(onEnd).toHaveBeenCalled()
    expect(onChunk.mock.calls[0][0].conversation_id).toBe('conv-live')
  })

  it('falls back to default conversation id when omitted', async () => {
    const onChunk = vi.fn()
    const onEnd = vi.fn()
    const dify = await import('../dify')

    await dify.difyQaChat('问题', { onChunk, onEnd })

    expect(onChunk.mock.calls[0][0].conversation_id).toBe('conv_mock')
    expect(onEnd.mock.calls[0][0].conversation_id).toBe('conv_mock')
  })

  it('falls back when qa answer chunks cannot be split', async () => {
    vi.doMock('@/mock/data', () => ({
      mockQaAnswer: '',
    }))
    vi.resetModules()
    const onChunk = vi.fn()
    const dify = await import('../dify')
    await dify.difyQaChat('空回答', { onChunk })
    expect(onChunk).toHaveBeenCalled()
    vi.doUnmock('@/mock/data')
    vi.resetModules()
  })
})
