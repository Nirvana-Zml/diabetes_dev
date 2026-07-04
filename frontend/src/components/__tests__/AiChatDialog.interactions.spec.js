import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  chatQA: vi.fn(),
}))

vi.mock('@/api/chat', () => ({
  chatQA: mocks.chatQA,
}))

const stubs = {
  'el-dialog': {
    template: '<div><slot /><slot name="footer" /></div>',
  },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'keydown'],
    template: '<textarea :value="modelValue" @input="$emit(`update:modelValue`, $event.target.value)" />',
  },
  'el-button': {
    emits: ['click'],
    template: '<button @click="$emit(`click`)"><slot /></button>',
  },
  MarkdownContent: {
    props: ['content'],
    template: '<div>{{ content }}</div>',
  },
  DisclaimerBar: {
    template: '<div />',
  },
}

function setupOf(wrapper) {
  return wrapper.vm.$.setupState
}

function assignMsgRef(setup, el) {
  if (setup.msgRef && typeof setup.msgRef === 'object' && 'value' in setup.msgRef) {
    setup.msgRef.value = el
  } else {
    setup.msgRef = el
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AiChatDialog interactions without changing component source', () => {
  it('sends a streaming message and stores conversation id', async () => {
    const AiChatDialog = (await import('../AiChatDialog.vue')).default
    mocks.chatQA.mockImplementation(async (_question, options) => {
      options.onChunk({ content: '控糖' })
      options.onChunk({ content: '建议' })
      options.onEnd({ conversation_id: 'conv-1' })
    })

    const wrapper = mount(AiChatDialog, {
      props: { modelValue: true },
      global: { stubs },
    })
    const setup = setupOf(wrapper)
    assignMsgRef(setup, { scrollHeight: 120, scrollTo: vi.fn() })
    await wrapper.find('textarea').setValue(' 怎么控制血糖 ')
    expect(setup.query).toBe(' 怎么控制血糖 ')
    setup.query = ' 怎么控制血糖 '

    await setup.send()
    await flushPromises()

    expect(mocks.chatQA).toHaveBeenCalledWith('怎么控制血糖', expect.objectContaining({
      conversationId: '',
    }))
    expect(setup.messages).toEqual(expect.arrayContaining([
      { role: 'user', content: '怎么控制血糖' },
      { role: 'assistant', content: '控糖建议' },
    ]))
    expect(setup.conversationId).toBe('conv-1')
    expect(setup.streaming).toBe(false)
  }, 10000)

  it('handles empty, busy and failed send branches', async () => {
    const AiChatDialog = (await import('../AiChatDialog.vue')).default
    const wrapper = mount(AiChatDialog, {
      props: { modelValue: true },
      global: { stubs },
    })
    const setup = setupOf(wrapper)

    setup.query = '   '
    await setup.send()
    expect(mocks.chatQA).not.toHaveBeenCalled()

    setup.query = '问题'
    setup.streaming = true
    await setup.send()
    expect(mocks.chatQA).not.toHaveBeenCalled()

    setup.streaming = false
    assignMsgRef(setup, { scrollHeight: 80, scrollTo: vi.fn() })
    mocks.chatQA.mockRejectedValueOnce(new Error('network'))
    await setup.send()
    await flushPromises()

    expect(setup.messages.at(-1).content).toBe('服务暂时繁忙，请稍后重试。')
    expect(setup.streaming).toBe(false)

    setup.query = '临时输入'
    setup.onClosed()
    expect(setup.query).toBe('')
  })

  it('adds welcome message when dialog opens for the first time', async () => {
    const AiChatDialog = (await import('../AiChatDialog.vue')).default
    const wrapper = mount(AiChatDialog, {
      props: { modelValue: false },
      global: { stubs },
    })
    const setup = setupOf(wrapper)

    expect(setup.messages).toEqual([])
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(setup.messages).toEqual([
      expect.objectContaining({
        role: 'assistant',
        content: expect.stringContaining('小糖助手'),
      }),
    ])

    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(setup.messages).toHaveLength(1)
  })
})
