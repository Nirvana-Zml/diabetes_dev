/**
 * AI 科普问答 — 经 home-service 代理（Milvus 检索 + Dify Chatbot SSE）
 */
import { USE_MOCK } from '@/config'
import { difyQaChat } from '@/api/dify'
import { fetchBackendSSE } from '@/utils/sse'

export function chatQA(query, options = {}) {
  if (USE_MOCK) {
    return difyQaChat(query, options)
  }
  const { conversationId, onChunk, onEnd } = options
  return fetchBackendSSE('/chat/qa', {
    body: {
      query,
      ...(conversationId ? { conversationId } : {}),
    },
    onEvent: ({ event, data }) => {
      if (event === 'error' || data.type === 'error') {
        throw new Error(data.message || '问答服务失败')
      }
      if ((event === 'message' || data.type === 'text') && data.content) {
        onChunk?.({
          type: 'text',
          content: data.content,
          conversation_id: data.conversationId || data.conversation_id,
        })
      }
      if (event === 'message_end' || data.type === 'end') {
        onEnd?.({
          type: 'end',
          conversation_id: data.conversationId || data.conversation_id,
          metadata: data.metadata,
        })
      }
    },
  })
}
