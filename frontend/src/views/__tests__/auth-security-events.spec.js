import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

const push = vi.fn()

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  resetPassword: vi.fn(),
  sendVerifyCode: vi.fn(),
  saveTokens: vi.fn(),
  clearTokens: vi.fn(),
  changePassword: vi.fn(),
  bindPhone: vi.fn(),
  bindEmail: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))

const route = vi.hoisted(() => ({
  query: {},
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => route,
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.success,
    error: mocks.error,
    warning: mocks.warning,
  },
}))

vi.mock('@element-plus/icons-vue', () => ({
  User: {},
  Lock: {},
}))

vi.mock('@/config', () => ({
  ADMIN_PORTAL_URL: 'http://admin.local',
}))

vi.mock('@/api/auth.js', () => ({
  login: mocks.login,
  register: mocks.register,
  resetPassword: mocks.resetPassword,
  sendVerifyCode: mocks.sendVerifyCode,
  saveTokens: mocks.saveTokens,
  clearTokens: mocks.clearTokens,
}))

vi.mock('@/api/auth', () => ({
  login: mocks.login,
  register: mocks.register,
  resetPassword: mocks.resetPassword,
  sendVerifyCode: mocks.sendVerifyCode,
  saveTokens: mocks.saveTokens,
  clearTokens: mocks.clearTokens,
}))

vi.mock('@/api/user', () => ({
  changePassword: mocks.changePassword,
  bindPhone: mocks.bindPhone,
  bindEmail: mocks.bindEmail,
}))

const FormStub = defineComponent({
  name: 'ElForm',
  setup(_props, { slots, expose, emit }) {
    expose({
      validate: vi.fn(async () => true),
      validateField: vi.fn(async () => true),
      clearValidate: vi.fn(),
    })
    return () => h('form', {
      onSubmit: (event) => {
        event.preventDefault()
        emit('submit', event)
      },
    }, slots.default?.())
  },
})

const InputStub = defineComponent({
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { emit, attrs }) {
    return () => h('input', {
      ...attrs,
      value: props.modelValue ?? '',
      onInput: (event) => emit('update:modelValue', event.target.value),
      onKeyup: (event) => attrs.onKeyup?.(event),
    })
  },
})

const ButtonStub = defineComponent({
  name: 'ElButton',
  emits: ['click'],
  setup(_props, { emit, slots }) {
    return () => h('button', {
      type: 'button',
      onClick: (event) => emit('click', event),
    }, slots.default?.())
  },
})

const CheckboxStub = defineComponent({
  name: 'ElCheckbox',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () => h('label', [
      h('input', {
        type: 'checkbox',
        checked: !!props.modelValue,
        onChange: (event) => emit('update:modelValue', event.target.checked),
      }),
      slots.default?.(),
    ])
  },
})

const RadioGroupStub = defineComponent({
  name: 'ElRadioGroup',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  setup(props, { emit, slots }) {
    return () => h('div', {
      onClick: () => {
        const next = props.modelValue === 'phone' ? 'email' : 'phone'
        emit('update:modelValue', next)
        emit('change', next)
      },
    }, slots.default?.())
  },
})

const passThrough = (name) => defineComponent({
  name,
  setup(_props, { slots, emit, attrs }) {
    return () => h('div', {
      ...attrs,
      onClick: (event) => attrs.onClick?.(event),
      onUpdateModelValue: (value) => emit('update:modelValue', value),
    }, slots.default?.())
  },
})

const stubs = {
  'router-link': passThrough('RouterLink'),
  'el-form': FormStub,
  'el-form-item': passThrough('ElFormItem'),
  'el-input': InputStub,
  'el-button': ButtonStub,
  'el-checkbox': CheckboxStub,
  'el-radio-group': RadioGroupStub,
  'el-radio': passThrough('ElRadio'),
  'el-dialog': passThrough('ElDialog'),
  'el-tabs': passThrough('ElTabs'),
  'el-tab-pane': passThrough('ElTabPane'),
}

function mountWithStubs(component, props = {}) {
  return mount(component, {
    props,
    global: {
      stubs,
      components: stubs,
    },
  })
}

async function setInputs(wrapper, values) {
  const inputs = wrapper.findAllComponents(InputStub)
  for (const [index, value] of values.entries()) {
    inputs[index].vm.$emit('update:modelValue', value)
  }
  await flushPromises()
}

function setupOf(wrapper) {
  return wrapper.vm.$.setupState
}

function validForm() {
  return {
    validate: vi.fn(async () => true),
    validateField: vi.fn(async () => true),
    clearValidate: vi.fn(),
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  route.query = {}
  localStorage.clear()
})

describe('auth and security template events', () => {
  it('submits login form and follows redirect branch', async () => {
    const Login = (await import('../Login/index.vue')).default
    route.query = { redirect: '/user-center' }
    mocks.login.mockResolvedValue({ access_token: 'token', role: 'user' })

    const wrapper = mountWithStubs(Login)
    const setup = setupOf(wrapper)
    setup.formRef = validForm()
    Object.assign(setup.form, { username: ' testuser ', password: '123456', remember: true })
    await setup.handleSubmit()
    await flushPromises()

    expect(mocks.login).toHaveBeenCalledWith({ username: 'testuser', password: '123456' })
    expect(mocks.saveTokens).toHaveBeenCalled()
    expect(localStorage.getItem('remember_username')).toBe('testuser')
    expect(push).toHaveBeenCalledWith('/user-center')
    expect(mocks.success).toHaveBeenCalledWith('登录成功')
  })

  it('submits register form and handles error branch', async () => {
    const Register = (await import('../Register/index.vue')).default
    mocks.register.mockResolvedValueOnce({})

    const wrapper = mountWithStubs(Register)
    const setup = setupOf(wrapper)
    setup.formRef = validForm()
    Object.assign(setup.form, {
      username: ' newuser ',
      phone: '13800000000',
      password: '123456',
      confirmPassword: '123456',
    })
    await setup.handleSubmit()
    await flushPromises()

    expect(mocks.register).toHaveBeenCalledWith({
      username: 'newuser',
      phone: '13800000000',
      password: '123456',
    })
    expect(push).toHaveBeenCalledWith('/login')

    mocks.register.mockRejectedValueOnce(new Error('用户名已存在'))
    await setup.handleSubmit()
    expect(mocks.error).toHaveBeenCalledWith('用户名已存在')
  })

  it('sends reset code and submits forgot password form', async () => {
    vi.useFakeTimers()
    const ForgotPassword = (await import('../ForgotPassword/index.vue')).default
    mocks.sendVerifyCode.mockResolvedValue({})
    mocks.resetPassword.mockResolvedValue({})

    const wrapper = mountWithStubs(ForgotPassword)
    const setup = setupOf(wrapper)
    setup.formRef = validForm()
    Object.assign(setup.form, {
      verifyType: 'phone',
      account: '13800000000',
      code: '123456',
      newPassword: 'abcdef',
      confirmPassword: 'abcdef',
    })

    await setup.handleSendCode()
    await flushPromises()
    expect(mocks.sendVerifyCode).toHaveBeenCalledWith({
      account: '13800000000',
      type: 'phone',
      purpose: 'reset_password',
    })
    expect(mocks.success).toHaveBeenCalledWith('验证码已发送')

    await setup.handleSubmit()
    await flushPromises()
    expect(mocks.resetPassword).toHaveBeenCalledWith({
      account: '13800000000',
      code: '123456',
      new_password: 'abcdef',
    })
    expect(push).toHaveBeenCalledWith('/login')

    vi.runOnlyPendingTimers()
    vi.useRealTimers()
  })

  it('triggers security dialog password, phone and email flows', async () => {
    const SecurityDialog = (await import('../UserCenter/components/SecurityDialog.vue')).default
    mocks.changePassword.mockResolvedValue({})
    mocks.bindPhone.mockResolvedValue({ phone: '13800000000' })
    mocks.bindEmail.mockResolvedValue({ email: 'new@example.com' })
    mocks.sendVerifyCode.mockResolvedValue({})

    const wrapper = mountWithStubs(SecurityDialog, {
      modelValue: true,
      profile: { phone: '', email: '' },
    })
    const setup = setupOf(wrapper)
    setup.pwdRef = validForm()
    setup.emailRef = validForm()

    Object.assign(setup.pwdForm, {
      old_password: 'oldpass',
      new_password: 'newpass',
      confirm_password: 'newpass',
    })
    Object.assign(setup.phoneForm, {
      phone: '13800000000',
      code: '654321',
    })
    Object.assign(setup.emailForm, {
      email: 'new@example.com',
      code: '111111',
    })

    await setup.submitPassword()
    await flushPromises()
    expect(mocks.changePassword).toHaveBeenCalledWith({
      old_password: 'oldpass',
      new_password: 'newpass',
    })
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()

    await setup.sendPhoneCode()
    await flushPromises()
    expect(mocks.sendVerifyCode).toHaveBeenCalledWith({
      account: '13800000000',
      type: 'phone',
      purpose: 'bind',
    })

    await setup.bindPhoneSubmit()
    await flushPromises()
    expect(mocks.bindPhone).toHaveBeenCalledWith({
      phone: '13800000000',
      verify_code: '654321',
    })

    await setup.sendEmailCode()
    await flushPromises()
    expect(mocks.sendVerifyCode).toHaveBeenCalledWith({
      account: 'new@example.com',
      type: 'email',
      purpose: 'bind',
    })

    await setup.bindEmailSubmit()
    await flushPromises()
    expect(mocks.bindEmail).toHaveBeenCalledWith({
      email: 'new@example.com',
      verify_code: '111111',
    })
    expect(wrapper.emitted('saved')).toBeTruthy()
  })
})
