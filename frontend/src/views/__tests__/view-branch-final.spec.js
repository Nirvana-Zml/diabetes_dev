import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  chatQA: vi.fn(),
  voiceToText: vi.fn(),
  recording: { value: false },
  startVoice: vi.fn(),
  stopVoice: vi.fn(),
  push: vi.fn(),
  route: { path: '/consultation', fullPath: '/consultation', query: {}, params: {}, meta: {} },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
}))

vi.mock('@/api/chat', () => ({ chatQA: mocks.chatQA, voiceToText: mocks.voiceToText }))
vi.mock('@/composables/useVoiceInput', () => ({
  useVoiceInput: () => ({
    recording: mocks.recording,
    start: mocks.startVoice,
    stop: mocks.stopVoice,
  }),
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
}))
vi.mock('@/config', () => ({ DISCLAIMER: '⚠️ test' }))
vi.mock('@/components/layout/SiteLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))
vi.mock('@/components/MarkdownContent.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/VoiceStatusBar.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ profile: {}, nickname: '用户', fetchProfile: vi.fn() }) }))
vi.mock('@/router', () => ({ default: { push: mocks.push, replace: vi.fn(), back: vi.fn() } }))
vi.mock('@/api/home', () => ({ getDoctors: vi.fn(async () => []) }))
vi.mock('@/utils/navigation', () => ({ safeBack: vi.fn() }))
vi.mock('@/composables/useMessageCenter', () => ({
  useMessageCenter: () => ({ markReadByBiz: vi.fn() }),
}))
vi.mock('@/api/consultation', () => ({
  getDepartments: vi.fn(async () => []),
  createConsultation: vi.fn(async () => ({ session_id: 's1' })),
  getConsultMessages: vi.fn(async () => []),
  sendConsultMessage: vi.fn(async () => ({})),
  getAiSuggest: vi.fn(async () => ({})),
  closeConsultation: vi.fn(async () => ({})),
}))

const stubs = ['el-button', 'el-input', 'el-select', 'el-option', 'el-drawer', 'el-tag', 'el-rate', 'el-icon']

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
  mocks.recording.value = false
})

describe('Assistant branch final', () => {
  it('covers welcomeMsg and isThinking branches', async () => {
    const Assistant = (await import('../Assistant/index.vue')).default
    const wrapper = shallowMount(Assistant, { global: { stubs } })
    await flush()
    const setup = setupOf(wrapper)

    setup.messages = [{ role: 'user', content: '问题' }]
    expect(setup.welcomeMsg).toBeNull()
    expect(setup.chatMessages).toHaveLength(1)

    setup.messages = [
      { role: 'assistant', content: '欢迎' },
      { role: 'assistant', content: '' },
    ]
    setup.streaming = true
    expect(setup.isThinking(setup.messages[1], 0)).toBe(true)

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['v'], { type: 'audio/webm' }))
    mocks.voiceToText.mockResolvedValueOnce({ text: '' })
    await setup.toggleVoice()

    wrapper.unmount()
  })
})

describe('Consultation branch final', () => {
  it('covers hasAiSuggestion and voice without text', async () => {
    const Consultation = (await import('../Consultation/index.vue')).default
    const wrapper = shallowMount(Consultation, { global: { stubs, directives: { loading: {} } } })
    await flush()
    const setup = setupOf(wrapper)

    setup.aiSuggestion = { suggestedQuestions: ['q1'] }
    expect(setup.hasAiSuggestion).toBe(true)

    mocks.recording.value = true
    mocks.stopVoice.mockResolvedValueOnce(new Blob(['v'], { type: 'audio/webm' }))
    mocks.voiceToText.mockResolvedValueOnce({ text: '' })
    await setup.toggleVoice()

    wrapper.unmount()
  })
})
