import { delay } from './delay'
import { API_BASE, USE_MOCK } from '@/config'

/**
 * 模拟 SSE 流式输出（占位，不对接真实 Dify SSE）
 */
export async function mockSSEStream(chunks, onChunk, onEnd, intervalMs = 80) {
  for (let i = 0; i < chunks.length; i++) {
    await delay(intervalMs)
    onChunk(chunks[i], i)
  }
  onEnd?.()
}

/**
 * 调用后端 SSE 接口（如方案生成 POST /plan/generate、科普问答 POST /chat/qa）
 * @param {string} path 相对 API_BASE 的路径
 * @param {((event: { event: string, data: object }) => void)|object} onEventOrOptions
 */
export async function fetchBackendSSE(path, onEventOrOptions) {
  let onEvent
  let method = 'POST'
  let body
  if (typeof onEventOrOptions === 'function') {
    onEvent = onEventOrOptions
  } else {
    onEvent = onEventOrOptions.onEvent
    method = onEventOrOptions.method || 'POST'
    body = onEventOrOptions.body
  }

  const token = localStorage.getItem('access_token')
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      Accept: 'text/event-stream',
      ...(body != null ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body != null ? { body: JSON.stringify(body) } : {}),
  })
  if (!res.ok) {
    const text = (await res.text()).trim()
    if (text) {
      try {
        const body = JSON.parse(text)
        throw new Error(body.message || `SSE 请求失败: ${res.status}`)
      } catch (e) {
        if (e instanceof SyntaxError) {
          throw new Error(text)
        }
        throw e
      }
    }
    throw new Error(`SSE 请求失败: HTTP ${res.status}`)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() || ''
    for (const block of parts) {
      let eventName = 'message'
      let dataStr = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        if (line.startsWith('data:')) dataStr = line.slice(5).trim()
      }
      if (dataStr) {
        try {
          onEvent({ event: eventName, data: JSON.parse(dataStr) })
        } catch {
          onEvent({ event: eventName, data: { raw: dataStr } })
        }
      }
    }
  }
}
