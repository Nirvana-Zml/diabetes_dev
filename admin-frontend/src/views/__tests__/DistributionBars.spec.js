import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DistributionBars from '../Statistics/DistributionBars.vue'

describe('DistributionBars', () => {
  it('renders empty state', () => {
    const wrapper = mount(DistributionBars, { props: { items: [] } })
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('normalizes labels and bar widths', () => {
    const wrapper = mount(DistributionBars, {
      props: {
        items: [
          { key: 'male', value: 10 },
          { label: '女', value: 5 },
          { label: '未知', value: 'bad' },
        ],
        emptyText: '无分布',
      },
    })
    expect(wrapper.text()).toContain('male')
    expect(wrapper.text()).toContain('女')
    expect(wrapper.findAll('.bar-fill').length).toBe(3)
  })
})
