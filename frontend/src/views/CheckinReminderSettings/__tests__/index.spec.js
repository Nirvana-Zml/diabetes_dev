import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  getUserProfile: vi.fn(),
  getReminderRules: vi.fn(),
  getReminderDefaults: vi.fn(),
  saveReminderRules: vi.fn(),
  isNotificationSupported: vi.fn(),
  getNotificationPermission: vi.fn(),
  requestNotificationPermission: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('@/api/user', () => ({
  getUserProfile: mocks.getUserProfile,
}))

vi.mock('@/api/checkinReminder', () => ({
  getReminderRules: mocks.getReminderRules,
  getReminderDefaults: mocks.getReminderDefaults,
  saveReminderRules: mocks.saveReminderRules,
}))

vi.mock('@/utils/notification', () => ({
  isNotificationSupported: mocks.isNotificationSupported,
  getNotificationPermission: mocks.getNotificationPermission,
  requestNotificationPermission: mocks.requestNotificationPermission,
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.success,
    warning: mocks.warning,
    error: mocks.error,
  },
}))

vi.mock('@element-plus/icons-vue', () => ({
  Delete: { name: 'Delete', template: '<i />' },
  Plus: { name: 'Plus', template: '<i />' },
}))

vi.mock('@/components/layout/SiteLayout.vue', () => ({
  default: {
    name: 'SiteLayout',
    props: ['title', 'showBack'],
    template: '<section><slot /></section>',
  },
}))

const SiteLayoutStub = defineComponent({
  name: 'SiteLayout',
  props: ['title', 'showBack'],
  template: '<section><slot /></section>',
})

const ButtonStub = defineComponent({
  name: 'ElButton',
  props: ['type', 'link', 'disabled', 'loading'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>',
})

const global = {
  stubs: {
    SiteLayout: SiteLayoutStub,
    'el-tag': defineComponent({
      name: 'ElTag',
      template: '<span><slot /></span>',
    }),
    'el-button': ButtonStub,
    'el-switch': defineComponent({
      name: 'ElSwitch',
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
    }),
    'el-time-picker': defineComponent({
      name: 'ElTimePicker',
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    }),
    'el-icon': defineComponent({
      name: 'ElIcon',
      template: '<span><slot /></span>',
    }),
  },
  directives: {
    loading: {},
  },
}

async function flush() {
  for (let i = 0; i < 8; i += 1) {
    await Promise.resolve()
  }
  await nextTick()
}

async function mountPage() {
  const Page = (await import('../index.vue')).default
  const wrapper = mount(Page, { global })
  await flush()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getUserProfile.mockResolvedValue({ privacy_settings: { checkin_notify: true } })
  mocks.getReminderRules.mockResolvedValue([
    { checkin_type: 1, remind_time: '08:30:00', sort_order: 2, enabled: true },
    { checkin_type: 2, remind_time: '17:30', enabled: false },
    { checkin_type: 9, remind_time: '10:00', enabled: true },
  ])
  mocks.getReminderDefaults.mockResolvedValue([
    { checkin_type: 1, remind_time: '08:00', sort_order: 0 },
    { checkin_type: 2, remind_time: '17:30', sort_order: 0 },
    { checkin_type: 3, remind_time: '08:00', sort_order: 0 },
    { checkin_type: 4, remind_time: '07:00', sort_order: 0 },
  ])
  mocks.saveReminderRules.mockResolvedValue([])
  mocks.isNotificationSupported.mockReturnValue(true)
  mocks.getNotificationPermission.mockReturnValue('default')
  mocks.requestNotificationPermission.mockResolvedValue('granted')
})

describe('CheckinReminderSettings view', () => {
  it('loads api rules and renders main controls', async () => {
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('全局打卡提醒')
    expect(wrapper.vm.checkinNotify).toBe(true)
    expect(wrapper.vm.rulesByType[1]).toEqual([
      expect.objectContaining({ remind_time: '08:30', sort_order: 2, enabled: true }),
    ])
    expect(wrapper.vm.rulesByType[2]).toEqual([
      expect.objectContaining({ remind_time: '17:30', enabled: false }),
    ])
    expect(wrapper.vm.rulesByType[3]).toEqual([
      expect.objectContaining({ remind_time: '08:00', sort_order: 0 }),
    ])
    expect(wrapper.vm.browserNotifyLabel).toBe('未授权')
    expect(wrapper.vm.browserBadgeClass).toBe('is-off')

    await wrapper.find('.text-link').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/user-center',
      query: { section: 'checkin-notify' },
    })
  })

  it('falls back to defaults when no saved rules exist', async () => {
    mocks.getUserProfile.mockResolvedValueOnce({ privacySettings: { checkinNotify: false } })
    mocks.getReminderRules.mockResolvedValueOnce([])
    const wrapper = await mountPage()

    expect(wrapper.vm.checkinNotify).toBe(false)
    expect(mocks.getReminderDefaults).toHaveBeenCalled()
    expect(wrapper.vm.rulesByType[4][0].remind_time).toBe('07:00')
  })

  it('applies defaults, edits slots and saves normalized payload', async () => {
    const wrapper = await mountPage()

    const tabButtons = wrapper.findAll('.type-switcher-btn')
    await tabButtons[1].trigger('click')
    expect(wrapper.vm.activeType).toBe('2')

    const switches = wrapper.findAllComponents({ name: 'ElSwitch' })
    await switches[1].find('input').setValue(true)
    expect(wrapper.vm.typeEnabled[2]).toBe(true)

    const timePickers = wrapper.findAllComponents({ name: 'ElTimePicker' })
    await timePickers[0].find('input').setValue('09:15')
    expect(wrapper.vm.rulesByType[1][0].remind_time).toBe('09:15')

    await wrapper.find('.add-slot-btn').trigger('click')
    expect(wrapper.vm.rulesByType[1]).toHaveLength(2)
    await wrapper.find('.delete-btn:not(:disabled)').trigger('click')
    expect(wrapper.vm.rulesByType[1]).toHaveLength(1)

    await wrapper.vm.applyDefaults()
    expect(mocks.success).toHaveBeenCalledWith('已加载推荐时段')

    wrapper.vm.addSlot(1)
    expect(wrapper.vm.rulesByType[1].at(-1)).toMatchObject({
      remind_time: '12:00',
      sort_order: wrapper.vm.rulesByType[1].length - 1,
    })
    wrapper.vm.removeSlot(1, 0)
    expect(wrapper.vm.rulesByType[1]).toHaveLength(1)

    wrapper.vm.typeEnabled[2] = false
    wrapper.vm.rulesByType[2][0].remind_time = '18:45:00'
    await wrapper.vm.handleSave()
    expect(mocks.saveReminderRules).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({
        checkin_type: 2,
        remind_time: '18:45',
        enabled: false,
        sort_order: 0,
      }),
    ]))
    expect(mocks.success).toHaveBeenCalledWith('提醒设置已保存')
  })

  it('handles permission states and unsupported environments', async () => {
    const wrapper = await mountPage()

    mocks.requestNotificationPermission.mockResolvedValueOnce('granted')
    await wrapper.vm.enableBrowserNotify()
    expect(mocks.success).toHaveBeenCalledWith('浏览器通知已开启')

    mocks.requestNotificationPermission.mockResolvedValueOnce('denied')
    await wrapper.vm.enableBrowserNotify()
    expect(mocks.warning).toHaveBeenCalledWith('您已拒绝浏览器通知，请在浏览器设置中手动开启')

    mocks.requestNotificationPermission.mockResolvedValueOnce('unsupported')
    await wrapper.vm.enableBrowserNotify()
    expect(mocks.warning).toHaveBeenCalledWith('当前环境不支持浏览器通知')

    wrapper.unmount()

    mocks.getNotificationPermission.mockReturnValue('granted')
    const grantedWrapper = await mountPage()
    expect(grantedWrapper.vm.browserNotifyLabel).toBe('已授权')
    expect(grantedWrapper.vm.browserBadgeClass).toBe('is-on')
    grantedWrapper.unmount()

    mocks.getNotificationPermission.mockReturnValue('denied')
    const deniedWrapper = await mountPage()
    expect(deniedWrapper.vm.browserNotifyLabel).toBe('已拒绝')
    expect(deniedWrapper.vm.browserBadgeClass).toBe('is-denied')
    deniedWrapper.unmount()

    mocks.isNotificationSupported.mockReturnValue(false)
    const unsupportedWrapper = await mountPage()
    expect(unsupportedWrapper.vm.notificationSupported).toBe(false)
    expect(unsupportedWrapper.vm.browserNotifyLabel).toBe('不支持')
  })

  it('handles load, defaults and save errors', async () => {
    mocks.getUserProfile.mockRejectedValueOnce(new Error('加载异常'))
    await mountPage()
    expect(mocks.error).toHaveBeenCalledWith('加载异常')

    const wrapper = await mountPage()
    mocks.getReminderDefaults.mockRejectedValueOnce(new Error('默认异常'))
    await wrapper.vm.applyDefaults()
    expect(mocks.error).toHaveBeenCalledWith('默认异常')

    mocks.saveReminderRules.mockRejectedValueOnce(new Error('保存异常'))
    await wrapper.vm.handleSave()
    expect(mocks.error).toHaveBeenCalledWith('保存异常')
    expect(wrapper.vm.saving).toBe(false)
  })
})
