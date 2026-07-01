import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownContent from '../MarkdownContent.vue'

describe('MarkdownContent', () => {
  it('renders markdown and escapes raw html', () => {
    const wrapper = mount(MarkdownContent, {
      props: {
        content: '## 标题\n\n**重点** <script>alert(1)</script>\nhttps://example.com',
      },
    })

    expect(wrapper.find('h2').text()).toBe('标题')
    expect(wrapper.find('strong').text()).toBe('重点')
    expect(wrapper.html()).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(wrapper.find('a').attributes('href')).toBe('https://example.com')
  })

  it('renders empty content by default', () => {
    const wrapper = mount(MarkdownContent)
    expect(wrapper.find('.markdown-body').text()).toBe('')
  })
})
