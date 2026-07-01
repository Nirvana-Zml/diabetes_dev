import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  route: { path: '/home', meta: {} },
  push: vi.fn(),
  back: vi.fn(),
  loggedIn: false,
  redirectToLogin: vi.fn((redirect) => ({ path: '/login', query: { redirect } })),
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({
    push: mocks.push,
    back: mocks.back,
  }),
}))

vi.mock('@/utils/auth', () => ({
  isLoggedIn: () => mocks.loggedIn,
  redirectToLogin: mocks.redirectToLogin,
}))

vi.mock('@element-plus/icons-vue', () => ({
  ArrowLeft: { name: 'ArrowLeft', template: '<i />' },
  Calendar: { name: 'Calendar', template: '<i />' },
  FirstAidKit: { name: 'FirstAidKit', template: '<i />' },
  HomeFilled: { name: 'HomeFilled', template: '<i />' },
  MagicStick: { name: 'MagicStick', template: '<i />' },
  User: { name: 'User', template: '<i />' },
}))

const dialogStub = defineComponent({
  name: 'ElDialog',
  emits: ['update:modelValue', 'closed'],
  template: '<div><slot /></div>',
})

const global = {
  stubs: {
    'el-icon': defineComponent({
      name: 'ElIcon',
      template: '<span><slot /></span>',
    }),
    RouterView: defineComponent({
      name: 'RouterView',
      template: '<main>route content</main>',
    }),
  },
}

beforeEach(() => {
  mocks.route.path = '/home'
  mocks.route.meta = {}
  mocks.loggedIn = false
  mocks.push.mockClear()
  mocks.back.mockClear()
  mocks.redirectToLogin.mockClear()
})

describe('layout and shell components', () => {
  it('renders App layout class from route meta', async () => {
    const App = (await import('../../App.vue')).default
    const wrapper = mount(App, { global })
    expect(wrapper.classes()).toContain('app-main')

    mocks.route.meta = { layout: 'auth' }
    const authWrapper = mount(App, { global })
    expect(authWrapper.classes()).toContain('app-root')
  })

  it('handles top and bottom navigation branches', async () => {
    const TopNav = (await import('../layout/TopNav.vue')).default
    const BottomNav = (await import('../layout/BottomNav.vue')).default

    const top = mount(TopNav, { props: { title: '测试标题', showBack: true }, global })
    expect(top.text()).toContain('测试标题')
    await top.find('.nav-left').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/home')
    await top.find('.nav-btn').trigger('click')
    expect(mocks.back).toHaveBeenCalled()

    const bottom = mount(BottomNav, { global })
    expect(bottom.find('.nav-item.active').text()).toContain('首页')
    await bottom.findAll('.nav-item')[0].trigger('click')
    expect(mocks.push).toHaveBeenCalledTimes(1)

    await bottom.findAll('.nav-item')[1].trigger('click')
    expect(mocks.redirectToLogin).toHaveBeenCalledWith('/checkin-records')
    expect(mocks.push).toHaveBeenLastCalledWith({ path: '/login', query: { redirect: '/checkin-records' } })

    mocks.loggedIn = true
    await bottom.findAll('.nav-item')[4].trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith('/user-center')
  })

  it('covers site header, footer and layout navigation', async () => {
    const SiteHeader = (await import('../layout/SiteHeader.vue')).default
    const SiteFooter = (await import('../layout/SiteFooter.vue')).default
    const SiteLayout = (await import('../layout/SiteLayout.vue')).default
    const AppLayout = (await import('../layout/AppLayout.vue')).default

    const header = mount(SiteHeader, { global })
    expect(header.text()).toContain('登录/注册')
    await header.find('.header-brand').trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith('/home')
    await header.findAll('.nav-link').at(-1).trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith('/login')

    mocks.loggedIn = true
    const loggedHeader = mount(SiteHeader, { global })
    expect(loggedHeader.text()).toContain('我的')
    await loggedHeader.findAll('.nav-link').at(-1).trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith('/user-center')
    mocks.loggedIn = false
    await loggedHeader.findAll('.nav-link').at(-1).trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith({ path: '/login', query: { redirect: '/user-center' } })
    mocks.route.path = '/login'
    await nextTick()
    expect(loggedHeader.findAll('.nav-link').some((item) => item.classes('active'))).toBe(true)

    mocks.loggedIn = false
    const footer = mount(SiteFooter, { global })
    await footer.findAll('button').find((button) => button.text() === '风险评估').trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith({ path: '/login', query: { redirect: '/health-evaluation' } })
    await footer.findAll('button').find((button) => button.text() === '知识科普').trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith('/health-info')
    mocks.loggedIn = true
    await footer.findAll('button').find((button) => button.text() === '健康方案').trigger('click')
    expect(mocks.push).toHaveBeenLastCalledWith('/living-plans')
    for (const label of ['医师咨询', '平台介绍', '医师入驻', '用户协议', '隐私政策', '帮助中心']) {
      await footer.findAll('button').find((button) => button.text() === label).trigger('click')
    }
    expect(mocks.push).toHaveBeenLastCalledWith('/assistant')

    const appLayout = mount(AppLayout, {
      props: { showNav: false, fullWidth: true },
      slots: { default: '<section>content</section>' },
      global,
    })
    expect(appLayout.classes()).toContain('app-layout--full')
    expect(appLayout.text()).toContain('content')
    const navLayout = mount(AppLayout, { global })
    expect(navLayout.findComponent({ name: 'BottomNav' }).exists()).toBe(true)

    const site = mount(SiteLayout, {
      props: { title: '页面标题', showBack: true, showFooter: true, fillHeight: true, fullBleed: true },
      slots: { default: '<section>页面内容</section>' },
      global,
    })
    expect(site.text()).toContain('页面标题')
    expect(site.text()).toContain('页面内容')
    await site.find('.back-btn').trigger('click')
    expect(mocks.back).toHaveBeenCalled()
    const plainSite = mount(SiteLayout, { global })
    expect(plainSite.find('.page-toolbar').exists()).toBe(false)
  })

  it('covers video player dialog events and reset branches', async () => {
    const pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
    const VideoPlayerDialog = (await import('../VideoPlayerDialog.vue')).default
    const wrapper = mount(VideoPlayerDialog, {
      props: {
        modelValue: true,
        video: { id: 'v1', title: '控糖视频', url: 'video.mp4', duration: '01:20' },
      },
      global: {
        stubs: {
          'el-dialog': dialogStub,
        },
      },
    })

    expect(wrapper.text()).toContain('时长 01:20')
    await wrapper.find('video').trigger('error')
    expect(wrapper.text()).toContain('视频加载失败')
    await wrapper.setProps({ video: { id: 'v2', title: '新视频', url: 'new.mp4' } })
    await nextTick()
    expect(wrapper.text()).not.toContain('视频加载失败')

    wrapper.findComponent({ name: 'ElDialog' }).vm.$emit('update:modelValue', false)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    wrapper.findComponent({ name: 'ElDialog' }).vm.$emit('closed')
    expect(pause).toHaveBeenCalled()

    const empty = mount(VideoPlayerDialog, {
      props: { modelValue: true, video: null },
      global: {
        stubs: {
          'el-dialog': dialogStub,
        },
      },
    })
    expect(empty.text()).toContain('视频暂不可用')
    pause.mockRestore()
  })
})
