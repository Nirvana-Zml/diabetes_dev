/**
 * AI 科普问答 — 经 home-service 代理（Milvus 检索 + Dify Chatbot SSE）
 */
import { USE_MOCK } from '@/config'
import { difyQaChat } from '@/api/dify'
import { fetchBackendSSE } from '@/utils/sse'
import { post } from '@/utils/request'
import { delay } from '@/utils/delay'
import { voiceBlobFilename, normalizeVoiceBlob } from '@/composables/useVoiceInput'

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

/** AI 科普助手语音识别 — 经 home-service 代理 Dify STT 工作流 */
export async function voiceToText(blob, options = {}) {
  if (USE_MOCK) {
    await delay(600)
    return {
      text: '糖尿病患者可以吃水果吗？',
      language: options.language || 'zh-CN',
    }
  }
  const form = new FormData()
  const prepared = await normalizeVoiceBlob(blob)
  form.append('audio', prepared, voiceBlobFilename(prepared))
  if (options.language) {
    form.append('language', options.language)
  }
  return post('/chat/voice', form, { timeout: 120000 })
}
