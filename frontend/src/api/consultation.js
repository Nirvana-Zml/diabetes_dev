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

function resolveConsultSessionList(data) {
  return data?.sessions || data?.list || []
}

export async function listConsultations(params = {}) {
  const page = params.page || 1
  const pageSize = params.page_size || params.size || 20
  const data = await getV2('/consultations', {
    params: {
      page,
      size: pageSize,
      status: params.status,
    },
  })
  const raw = resolveConsultSessionList(data)
  const list = raw.map((item) => {
    const s = toSnakeCase(item)
    return {
      session_id: s.session_id,
      doctor_id: s.doctor_id,
      doctor_name: s.doctor_name || s.name || 'AI 医生',
      doctor_title: s.doctor_title || s.title || '',
      department: s.department || '',
      status: s.status,
      started_at: s.started_at,
      ended_at: s.ended_at,
      last_message: s.last_message,
      message_count: s.message_count,
      rating: s.rating,
      feedback: s.feedback || '',
    }
  })
  return {
    list,
    total: data?.total ?? list.length,
    page,
    page_size: pageSize,
  }
}

export async function createConsultation(data) {
  const res = await postV2('/consultations', {
    doctor_id: data.doctor_id,
  })
  const session = toSnakeCase(res)
  session.session_id = session.session_id ?? res?.session_id ?? res?.sessionId ?? ''
  return session
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

/** @internal 供单元测试覆盖消息归一化分支 */
export function normalizeConsultMessageForTest(item) {
  return normalizeMessage(item)
}

/** @internal 供单元测试覆盖会话列表字段回退 */
export function resolveConsultSessionListForTest(data) {
  return resolveConsultSessionList(data)
}
