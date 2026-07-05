import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const chatMocks = vi.hoisted(() => ({
  chatQA: vi.fn(),
  voiceToText: vi.fn(),
  recording: { __v_isRef: true, value: false },
  startVoice: vi.fn(),
  stopVoice: vi.fn(),
  markMessageRead: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/chat', () => ({ chatQA: chatMocks.chatQA, voiceToText: chatMocks.voiceToText }))
vi.mock('@/composables/useVoiceInput', () => ({
  useVoiceInput: () => ({
    recording: chatMocks.recording,
    start: chatMocks.startVoice,
    stop: chatMocks.stopVoice,
  }),
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
}))
vi.mock('@/config', () => ({ DISCLAIMER: '⚠️', APP_NAME: '糖尿病智能助手' }))
vi.mock('@/components/MarkdownContent.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/DisclaimerBar.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/VoiceStatusBar.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/layout/SiteLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function setupOf(wrapper) {
  return wrapper.vm.$?.setupState || wrapper.vm
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  chatMocks.recording.value = false
})

describe('Assistant remaining branches', () => {
  it('covers mic permission, voice error and onEnd conversationId branches', async () => {
    const { ElMessage } = await import('element-plus')
    const Assistant = (await import('../Assistant/index.vue')).default
    const wrapper = shallowMount(Assistant, {
      global: { stubs: ['el-button', 'el-input', 'el-icon'] },
    })
    await flush()
    const setup = setupOf(wrapper)

    chatMocks.startVoice.mockRejectedValueOnce(new Error('denied'))
    await setup.toggleVoice()
    expect(ElMessage.warning).toHaveBeenCalledWith('无法访问麦克风，请检查浏览器权限')

    chatMocks.recording.value = true
    chatMocks.stopVoice.mockResolvedValueOnce(new Blob(['a'], { type: 'audio/webm' }))
    chatMocks.voiceToText.mockRejectedValueOnce({})
    await setup.toggleVoice()
    expect(ElMessage.error).toHaveBeenCalledWith('语音识别失败')

    setup.query = '问题'
    chatMocks.chatQA.mockImplementationOnce(async (_q, { onChunk, onEnd }) => {
      onChunk({ content: '回答' })
      onEnd({ conversationId: 'cid-new' })
    })
    await setup.send()
    expect(setup.conversationId).toBe('cid-new')

    wrapper.unmount()
  })
})
