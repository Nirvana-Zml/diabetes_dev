import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VoiceStatusBar from '../VoiceStatusBar.vue'

describe('VoiceStatusBar', () => {
  it('renders recording state', () => {
    const wrapper = mount(VoiceStatusBar, {
      props: { recording: true, transcribing: false },
    })
    expect(wrapper.text()).toContain('正在录音')
    expect(wrapper.find('.voice-status--recording').exists()).toBe(true)
  })

  it('renders transcribing state', () => {
    const wrapper = mount(VoiceStatusBar, {
      props: { recording: false, transcribing: true },
    })
    expect(wrapper.text()).toContain('正在将语音转为文字')
    expect(wrapper.find('.voice-status--transcribing').exists()).toBe(true)
  })

  it('hides when idle', () => {
    const wrapper = mount(VoiceStatusBar, {
      props: { recording: false, transcribing: false },
    })
    expect(wrapper.find('.voice-status').exists()).toBe(false)
  })
})
