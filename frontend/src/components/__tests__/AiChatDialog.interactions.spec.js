import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  chatQA: vi.fn(),
  voiceToText: vi.fn(),
  recordingRef: null,
  startVoice: vi.fn(),
  stopVoice: vi.fn(),
}))

vi.mock('@/api/chat', () => ({
  chatQA: mocks.chatQA,
  voiceToText: mocks.voiceToText,
}))

vi.mock('@/composables/useVoiceInput', () => ({
  useVoiceInput: () => ({
    recording: mocks.recordingRef,
    start: mocks.startVoice,
    stop: mocks.stopVoice,
  }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn() },
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

beforeEach(async () => {
  vi.clearAllMocks()
  const { ref } = await import('vue')
  mocks.recordingRef = ref(false)
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

  it('covers voice input branches', async () => {
    const { ElMessage } = await import('element-plus')
    const AiChatDialog = (await import('../AiChatDialog.vue')).default
    const wrapper = mount(AiChatDialog, {
      props: { modelValue: true },
      global: { stubs },
    })
    const setup = setupOf(wrapper)

    setup.streaming = true
    await setup.toggleVoice()
    expect(mocks.startVoice).not.toHaveBeenCalled()

    setup.streaming = false
    setup.transcribing = true
    await setup.toggleVoice()
    expect(mocks.startVoice).not.toHaveBeenCalled()

    setup.transcribing = false
    mocks.startVoice.mockRejectedValueOnce(new Error('denied'))
    await setup.toggleVoice()
    expect(ElMessage.warning).toHaveBeenCalledWith('无法访问麦克风，请检查浏览器权限')

    mocks.recordingRef.value = true
    mocks.stopVoice.mockResolvedValueOnce(null)
    await setup.toggleVoice()
    expect(ElMessage.warning).toHaveBeenCalledWith('录音时间过短，请重试')

    mocks.recordingRef.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['voice'], { type: 'audio/webm' }))
    mocks.voiceToText.mockResolvedValueOnce({ text: ' 血糖问题 ' })
    await setup.toggleVoice()
    expect(setup.query).toBe(' 血糖问题 ')

    mocks.recordingRef.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['voice'], { type: 'audio/webm' }))
    mocks.voiceToText.mockRejectedValueOnce(new Error('识别失败'))
    await setup.toggleVoice()
    expect(ElMessage.error).toHaveBeenCalledWith('识别失败')
    expect(setup.transcribing).toBe(false)
  })

  it('covers voice without text result and send scroll callbacks', async () => {
    const AiChatDialog = (await import('../AiChatDialog.vue')).default
    const wrapper = mount(AiChatDialog, {
      props: { modelValue: true },
      global: { stubs },
    })
    const setup = setupOf(wrapper)

    mocks.recordingRef.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['voice'], { type: 'audio/webm' }))
    mocks.voiceToText.mockResolvedValueOnce({ text: '' })
    await setup.toggleVoice()
    expect(setup.query).toBe('')

    setup.msgRef = { scrollHeight: 100, scrollTo: vi.fn() }
    setup.query = '问题'
    mocks.chatQA.mockImplementationOnce(async (_q, opts) => {
      opts.onChunk({ content: '片段' })
      opts.onEnd({ conversation_id: 'c1' })
    })
    await setup.send()
    await flushPromises()
    expect(setup.conversationId).toBe('c1')

    setup.streaming = false
    setup.query = '失败'
    mocks.chatQA.mockRejectedValueOnce(new Error('network'))
    await setup.send()
    await flushPromises()
    expect(setup.messages.at(-1).content).toContain('繁忙')
  })

  it('renders recording and transcribing placeholder branches', async () => {
    const AiChatDialog = (await import('../AiChatDialog.vue')).default
    const wrapper = mount(AiChatDialog, {
      props: { modelValue: true },
      global: {
        stubs: {
          ...stubs,
          'el-input': {
            props: ['modelValue', 'placeholder', 'disabled'],
            template: '<textarea :placeholder="placeholder" :disabled="disabled" />',
          },
        },
      },
    })
    mocks.recordingRef.value = true
    await flushPromises()
    expect(wrapper.find('textarea').attributes('placeholder')).toContain('聆听')
    mocks.recordingRef.value = false
    const setup = setupOf(wrapper)
    setup.transcribing = true
    await flushPromises()
    expect(wrapper.find('textarea').attributes('placeholder')).toContain('识别')

    mocks.recordingRef.value = true
    setup.transcribing = false
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['v'], { type: 'audio/webm' }))
    mocks.voiceToText.mockRejectedValueOnce({})
    const { ElMessage } = await import('element-plus')
    await setup.toggleVoice()
    expect(ElMessage.error).toHaveBeenCalledWith('语音识别失败')
  })
})
