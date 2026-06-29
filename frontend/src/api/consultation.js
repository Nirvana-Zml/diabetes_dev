/**
 * 医生咨询 API — 对接 consultation-service /api/v2
 */
import { getV2, postV2 } from '@/utils/request'
import { toSnakeCase } from '@/utils/normalize'

function normalizeMessage(item) {
  if (!item) return item
  const m = toSnakeCase(item)
  return {
    message_id: m.message_id ?? item.messageId ?? '',
    sender_type: m.sender_type ?? item.senderType ?? 'user',
    content: m.content ?? '',
    sent_at: m.sent_at ?? item.sentAt ?? new Date().toISOString(),
  }
}

/** 咨询页科室筛选项 */
export async function getDepartments() {
  const data = await getV2('/ai-doctors/departments')
  return Array.isArray(data) ? data : []
}

export async function createConsultation(data) {
  const res = await postV2('/consultations', {
    doctor_id: data.doctor_id,
  })
  return toSnakeCase(res)
}

export async function getConsultMessages(sessionId) {
  const data = await getV2(`/consultations/${sessionId}/messages`)
  const list = data?.messages || []
  return list.map(normalizeMessage)
}

export async function sendConsultMessage(sessionId, data) {
  const res = await postV2(
    `/consultations/${sessionId}/messages`,
    { content: data.content },
    { timeout: 180000 },
  )
  const userMsg = normalizeMessage(res.user_message || res.userMessage || res)
  const aiRaw = res.ai_message || res.aiMessage
  return {
    userMessage: userMsg,
    aiMessage: aiRaw ? normalizeMessage(aiRaw) : null,
  }
}

export async function getAiSuggest(sessionId) {
  return getV2(`/consultations/${sessionId}/ai-suggestion`)
}

export async function closeConsultation(sessionId, data) {
  return postV2(`/consultations/${sessionId}/close`, {
    rating: data.rating,
    feedback: data.feedback,
  })
}
