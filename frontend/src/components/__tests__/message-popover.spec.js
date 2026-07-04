import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  loadList: vi.fn(),
  refresh: vi.fn(),
  markAllMessagesRead: vi.fn(),
  markMessageRead: vi.fn(),
  messageList: { __v_isRef: true, value: [] },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('@/api/message', () => ({
  markAllMessagesRead: mocks.markAllMessagesRead,
  markMessageRead: mocks.markMessageRead,
}))

vi.mock('@/composables/useMessageCenter', () => ({
  useMessageCenter: () => ({
    loadList: mocks.loadList,
    refresh: mocks.refresh,
    messageList: mocks.messageList,
  }),
}))

async function flush() {
  await Promise.resolve()
  await Promise.resolve()
  await nextTick()
}

async function mountPopover(props = {}) {
  const MessagePopover = (await import('../MessageCenter/MessagePopover.vue')).default
  const wrapper = mount(MessagePopover, {
    props,
    global: {
      stubs: {
        transition: defineComponent({ template: '<slot />' }),
      },
    },
  })
  await flush()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-07-03T12:00:00+08:00'))
  mocks.loadList.mockResolvedValue()
  mocks.refresh.mockResolvedValue()
  mocks.markAllMessagesRead.mockResolvedValue()
  mocks.markMessageRead.mockResolvedValue()
  mocks.messageList.value = []
})

afterEach(() => {
  vi.useRealTimers()
})

describe('MessagePopover', () => {
  it('loads messages and renders empty or mobile states', async () => {
    const wrapper = await mountPopover({ mobile: true })

    expect(mocks.loadList).toHaveBeenCalled()
    expect(wrapper.classes()).toContain('message-popover--mobile')
    expect(wrapper.text()).toContain('暂无消息')
  })

  it('opens messages, marks read and builds target routes', async () => {
    mocks.markMessageRead.mockRejectedValueOnce(new Error('ignored'))
    mocks.messageList.value = [
      {
        message_id: 'm1',
        title: '失败消息',
        summary: '需要重试',
        status: 'failed',
        is_read: false,
        created_at: [2026, 7, 3, 11, 59, 45],
        link_path: '/checkin-records',
        link_query: { tab: 'food' },
      },
      {
        messageId: 'm2',
        title: '咨询回复',
        messageType: 'consult_reply',
        isRead: true,
        createdAt: '2026-07-03T10:30:00+08:00',
        linkPath: '/consultation/chat',
      },
      {
        title: '普通消息',
        createdAt: '2026-07-01T09:15:00+08:00',
      },
      {
        title: '异常时间',
        createdAt: 'bad-date',
      },
    ]
    const wrapper = await mountPopover()

    expect(wrapper.text()).toContain('返回重试')
    expect(wrapper.text()).toContain('进入会话')
    expect(wrapper.text()).toContain('查看')
    expect(wrapper.text()).toContain('刚刚')
    expect(wrapper.text()).toContain('1 小时前')
    expect(wrapper.text()).toContain('2026/07/01')

    await wrapper.find('.action-btn').trigger('click')
    await flush()

    expect(mocks.markMessageRead).toHaveBeenCalledWith('m1')
    expect(mocks.refresh).toHaveBeenCalled()
    expect(wrapper.emitted('open')).toHaveLength(1)
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/checkin-records',
      query: { tab: 'food' },
    })

    await wrapper.findAll('.action-btn')[2].trigger('click')
    await flush()

    expect(mocks.markMessageRead).toHaveBeenCalledTimes(1)
    expect(mocks.push).toHaveBeenLastCalledWith({
      path: '/home',
      query: {},
    })
  })

  it('marks all messages as read and resets loading state', async () => {
    mocks.messageList.value = [
      { message_id: 'm1', title: '提醒', created_at: '2026-07-02T08:00:00+08:00' },
    ]
    const wrapper = await mountPopover()

    await wrapper.find('.read-all-btn').trigger('click')
    await flush()

    expect(mocks.markAllMessagesRead).toHaveBeenCalled()
    expect(mocks.refresh).toHaveBeenCalled()
    expect(mocks.loadList).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.markingAll).toBe(false)
  })
})
