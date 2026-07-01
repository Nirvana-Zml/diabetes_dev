import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchBackendSSE, mockSSEStream } from '../sse'

function streamFromChunks(chunks) {
  const encoder = new TextEncoder()
  let index = 0
  return {
    getReader: () => ({
      read: vi.fn(async () => {
        if (index >= chunks.length) return { done: true }
        return { done: false, value: encoder.encode(chunks[index++]) }
      }),
    }),
  }
}

describe('sse utils', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('emits mock chunks in order and calls end callback', async () => {
    vi.useFakeTimers()
    const onChunk = vi.fn()
    const onEnd = vi.fn()

    const promise = mockSSEStream(['a', 'b'], onChunk, onEnd, 10)
    await vi.advanceTimersByTimeAsync(20)
    await promise

    expect(onChunk).toHaveBeenNthCalledWith(1, 'a', 0)
    expect(onChunk).toHaveBeenNthCalledWith(2, 'b', 1)
    expect(onEnd).toHaveBeenCalledTimes(1)
  })

  it('fetches backend SSE with token, body and parsed events', async () => {
    localStorage.setItem('access_token', 'token')
    const onEvent = vi.fn()
    globalThis.fetch = vi.fn(async () => ({
      ok: true,
      body: streamFromChunks([
        'event: answer\ndata: {"text":"hello"}\n\n',
        'data: raw text\n\n',
      ]),
    }))

    await fetchBackendSSE('/chat/qa', { method: 'PUT', body: { question: 'hi' }, onEvent })

    expect(fetch).toHaveBeenCalledWith('/api/v1/chat/qa', {
      method: 'PUT',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: 'Bearer token',
      },
      body: JSON.stringify({ question: 'hi' }),
    })
    expect(onEvent).toHaveBeenNthCalledWith(1, { event: 'answer', data: { text: 'hello' } })
    expect(onEvent).toHaveBeenNthCalledWith(2, { event: 'message', data: { raw: 'raw text' } })
  })

  it('uses default POST method for option objects without method', async () => {
    const onEvent = vi.fn()
    globalThis.fetch = vi.fn(async () => ({
      ok: true,
      body: streamFromChunks(['data: {"done":true}\n\n']),
    }))

    await fetchBackendSSE('/chat/qa', { onEvent })

    expect(fetch).toHaveBeenCalledWith('/api/v1/chat/qa', {
      method: 'POST',
      headers: { Accept: 'text/event-stream' },
    })
    expect(onEvent).toHaveBeenCalledWith({ event: 'message', data: { done: true } })
  })

  it('throws readable errors for failed SSE responses', async () => {
    globalThis.fetch = vi.fn(async () => ({
      ok: false,
      status: 500,
      text: async () => JSON.stringify({ message: '服务异常' }),
    }))

    await expect(fetchBackendSSE('/chat/qa', vi.fn())).rejects.toThrow('服务异常')

    globalThis.fetch = vi.fn(async () => ({
      ok: false,
      status: 503,
      text: async () => JSON.stringify({ error: 'no message' }),
    }))
    await expect(fetchBackendSSE('/chat/qa', vi.fn())).rejects.toThrow('SSE 请求失败: 503')

    globalThis.fetch = vi.fn(async () => ({
      ok: false,
      status: 400,
      text: async () => 'bad request',
    }))
    await expect(fetchBackendSSE('/chat/qa', vi.fn())).rejects.toThrow('bad request')

    globalThis.fetch = vi.fn(async () => ({
      ok: false,
      status: 404,
      text: async () => '',
    }))
    await expect(fetchBackendSSE('/chat/qa', vi.fn())).rejects.toThrow('SSE 请求失败: HTTP 404')
  })
})
