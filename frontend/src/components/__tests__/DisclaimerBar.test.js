import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { DISCLAIMER } from '@/config'
import DisclaimerBar from '../DisclaimerBar.vue'

describe('DisclaimerBar', () => {
  it('renders default disclaimer text', () => {
    const wrapper = mount(DisclaimerBar)
    expect(wrapper.text()).toBe(DISCLAIMER)
  })

  it('renders custom text', () => {
    const wrapper = mount(DisclaimerBar, { props: { text: '自定义提示' } })
    expect(wrapper.text()).toBe('自定义提示')
  })
})
