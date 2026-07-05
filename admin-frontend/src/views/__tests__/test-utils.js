import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { vi } from 'vitest'

export const routerPush = vi.fn()
export const routerReplace = vi.fn()
export const route = {
  path: '/home',
  fullPath: '/home',
  query: {},
  meta: {},
}

export function mountView(component, options = {}) {
  return mount(component, {
    global: {
      plugins: [ElementPlus],
      stubs: {
        'router-link': true,
        'router-view': true,
        MarkdownContent: { template: '<div class="markdown-stub" />' },
        AuditOverview: {
          template: '<div />',
          setup(_, { expose }) {
            expose({ loadOverview: vi.fn() })
            return {}
          },
        },
        DistributionBars: { template: '<div class="bars-stub" />' },
      },
    },
    ...options,
  })
}

export async function flush() {
  await Promise.resolve()
  await Promise.resolve()
}
