import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  getUserConsultations: vi.fn(),
  exportUserData: vi.fn(),
  changePassword: vi.fn(),
  bindPhone: vi.fn(),
  bindEmail: vi.fn(),
  sendVerifyCode: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  getUserConsultations: mocks.getUserConsultations,
  exportUserData: mocks.exportUserData,
  changePassword: mocks.changePassword,
  bindPhone: mocks.bindPhone,
  bindEmail: mocks.bindEmail,
  updateHealthRecord: vi.fn(async (d) => d),
}))

vi.mock('@/api/auth.js', () => ({
  sendVerifyCode: mocks.sendVerifyCode,
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: mocks.success, error: mocks.error, warning: mocks.warning },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
}))

const stubs = ['el-drawer', 'el-dialog', 'el-form', 'el-form-item', 'el-input', 'el-button', 'el-tag', 'el-rate', 'el-checkbox', 'el-checkbox-group', 'el-radio', 'el-radio-group', 'el-date-picker']

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function validFormRef() {
  return { validate: vi.fn(() => Promise.resolve(true)), validateField: vi.fn(() => Promise.resolve(true)) }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('UserCenter dialog branch coverage', () => {
  it('covers ConsultationDrawer list fallback and error message fallback', async () => {
    const ConsultationDrawer = (await import('../ConsultationDrawer.vue')).default
    mocks.getUserConsultations.mockResolvedValueOnce({})
    const okWrapper = shallowMount(ConsultationDrawer, { props: { modelValue: false }, global: { stubs } })
    await okWrapper.setProps({ modelValue: true })
    await flush()
    expect(okWrapper.vm.list).toEqual([])

    mocks.getUserConsultations.mockRejectedValueOnce({})
    const errWrapper = shallowMount(ConsultationDrawer, { props: { modelValue: false }, global: { stubs } })
    await errWrapper.setProps({ modelValue: true })
    await flush()
    expect(mocks.error).toHaveBeenCalledWith('加载咨询记录失败')
  })

  it('covers ExportDialog message-only success and error fallback', async () => {
    const ExportDialog = (await import('../ExportDialog.vue')).default
    const wrapper = shallowMount(ExportDialog, { props: { modelValue: true }, global: { stubs } })
    mocks.exportUserData.mockResolvedValueOnce({ message: '已提交' })
    await wrapper.vm.submit()
    expect(mocks.success).toHaveBeenCalledWith('已提交')

    mocks.exportUserData.mockRejectedValueOnce({})
    wrapper.vm.types = ['health']
    await wrapper.vm.submit()
    expect(mocks.error).toHaveBeenCalledWith('导出失败，请稍后重试')
  })

  it('covers SecurityDialog validator and submit error fallbacks', async () => {
    const SecurityDialog = (await import('../SecurityDialog.vue')).default
    const wrapper = shallowMount(SecurityDialog, {
      props: { modelValue: true, profile: { phone: '', email: '' } },
      global: { stubs },
    })

    const setup = wrapper.vm.$?.setupState || wrapper.vm

    const emailCb = vi.fn()
    setup.emailRules.email[0].validator({}, 'bad-email', emailCb)
    expect(emailCb.mock.calls[0][0]).toBeInstanceOf(Error)

    setup.pwdRef = validFormRef()
    setup.pwdForm.old_password = 'old'
    setup.pwdForm.new_password = 'new'
    setup.pwdForm.confirm_password = 'new'
    mocks.changePassword.mockResolvedValueOnce({ success: true })
    await setup.submitPassword()
    expect(mocks.success).toHaveBeenCalledWith('密码修改成功')

    wrapper.vm.phoneForm.phone = '13800138000'
    wrapper.vm.phoneForm.code = '123456'
    mocks.bindPhone.mockRejectedValueOnce({})
    await setup.bindPhoneSubmit()
    expect(mocks.error).toHaveBeenCalledWith('手机绑定失败')

    mocks.error.mockClear()
    setup.emailRef = { validateField: vi.fn(() => Promise.resolve(true)) }
    setup.emailForm.email = 'test@example.com'
    mocks.sendVerifyCode.mockRejectedValueOnce({})
    await setup.sendEmailCode()
    expect(mocks.error).toHaveBeenCalledWith('发送失败，请稍后重试')
  })
})
