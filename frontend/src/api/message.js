import { get, post } from '@/utils/request'

export function getMessages(params = {}) {
  return get('/user/messages', params)
}

export function getUnreadMessageCount() {
  return get('/user/messages/unread-count')
}

export function markMessageRead(messageId) {
  return post(`/user/messages/${messageId}/read`)
}

export function markAllMessagesRead() {
  return post('/user/messages/read-all')
}

export function markMessagesReadByBiz(messageType, bizId) {
  return post('/user/messages/read-by-biz', { messageType, bizId })
}
