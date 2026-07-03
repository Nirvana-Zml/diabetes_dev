import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  post: mocks.post,
}))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockResolvedValue({ list: [], unread_count: 0 })
  mocks.post.mockResolvedValue({ success: true })
})

describe('message api', () => {
  it('loads messages and unread count', async () => {
    const api = await import('../message')

    mocks.get.mockResolvedValueOnce({ list: [{ message_id: 'm1' }], unread_count: 2 })
    await expect(api.getMessages({ limit: 5 })).resolves.toMatchObject({
      list: [{ message_id: 'm1' }],
      unread_count: 2,
    })

    mocks.get.mockResolvedValueOnce({ unreadCount: 3 })
    await expect(api.getUnreadMessageCount()).resolves.toEqual({ unreadCount: 3 })
  })

  it('marks messages read', async () => {
    const api = await import('../message')

    await api.markMessageRead('m1')
    expect(mocks.post).toHaveBeenCalledWith('/user/messages/m1/read')

    await api.markAllMessagesRead()
    expect(mocks.post).toHaveBeenCalledWith('/user/messages/read-all')

    await api.markMessagesReadByBiz('consult_reply', 's1')
    expect(mocks.post).toHaveBeenCalledWith('/user/messages/read-by-biz', {
      messageType: 'consult_reply',
      bizId: 's1',
    })
  })
})
