import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import App from '../App.vue'

const routeMeta = ref({ layout: 'auth' })

vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: routeMeta.value }),
}))

describe('App', () => {
  it('uses auth layout class on login pages', () => {
    routeMeta.value = { layout: 'auth' }
    const wrapper = mount(App, {
      global: { stubs: { 'router-view': true } },
    })
    expect(wrapper.find('.app-root').exists()).toBe(true)
  })

  it('uses main layout class on admin pages', () => {
    routeMeta.value = {}
    const wrapper = mount(App, {
      global: { stubs: { 'router-view': true } },
    })
    expect(wrapper.find('.app-main').exists()).toBe(true)
  })
})
