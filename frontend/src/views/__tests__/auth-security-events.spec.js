import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

const push = vi.fn()
const replace = vi.fn()

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
  useRouter: () => ({ push, replace }),
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
  Iphone: {},
  Message: {},
  Key: {},
}))

vi.mock('@/config', () => ({
  APP_NAME: '糖尿病智能助手',
  ADMIN_PORTAL_URL: 'http://admin.local',
  APP_NAME: '糖尿病智能助手',
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
    mocks.login.mockResolvedValueOnce({ access_token: 'token', user: { role: 'user' } })

    const wrapper = mountWithStubs(Login)
    const setup = setupOf(wrapper)
    setup.formRef = validForm()
    Object.assign(setup.form, { username: ' testuser ', password: '123456', remember: true })
    await setup.handleSubmit()
    await flushPromises()

    expect(mocks.login).toHaveBeenCalledWith({ username: 'testuser', password: '123456' })
    expect(mocks.saveTokens).toHaveBeenCalled()
    expect(localStorage.getItem('remember_username')).toBe('testuser')
    expect(replace).toHaveBeenCalledWith('/user-center')
    expect(mocks.success).toHaveBeenCalledWith('登录成功')
  })

  it('resolves role from nested user object when role is missing', async () => {
    const Login = (await import('../Login/index.vue')).default
    mocks.login.mockResolvedValueOnce({ access_token: 'token', user: { role: 'user' } })

    const wrapper = mountWithStubs(Login)
    const setup = setupOf(wrapper)
    setup.formRef = validForm()
    Object.assign(setup.form, { username: 'nested', password: '123456', remember: false })
    await setup.handleSubmit()
    await flushPromises()

    expect(mocks.saveTokens).toHaveBeenCalled()
    expect(mocks.clearTokens).not.toHaveBeenCalled()
  })

  it('defaults role to user when login response omits role fields', async () => {
    const Login = (await import('../Login/index.vue')).default
    mocks.login.mockResolvedValueOnce({ access_token: 'token-only' })
    const wrapper = mountWithStubs(Login)
    const setup = setupOf(wrapper)
    setup.formRef = validForm()
    Object.assign(setup.form, { username: 'plain', password: '123456', remember: false })
    await setup.handleSubmit()
    await flushPromises()
    expect(mocks.saveTokens).toHaveBeenCalledWith({ access_token: 'token-only' })
  })

  it('covers auth page template model updates and validation short-circuit branches', async () => {
    const Login = (await import('../Login/index.vue')).default
    const Register = (await import('../Register/index.vue')).default
    const ForgotPassword = (await import('../ForgotPassword/index.vue')).default

    localStorage.setItem('remember_username', 'saved-user')
    const loginWrapper = mountWithStubs(Login)
    await flushPromises()
    const loginSetup = setupOf(loginWrapper)
    expect(loginSetup.form.username).toBe('saved-user')
    expect(loginSetup.form.remember).toBe(true)
    await setInputs(loginWrapper, ['typed-user', 'typed-pass'])
    loginWrapper.findComponent(CheckboxStub).vm.$emit('update:modelValue', false)
    expect(loginSetup.form.username).toBe('typed-user')
    expect(loginSetup.form.password).toBe('typed-pass')
    expect(loginSetup.form.remember).toBe(false)
    loginSetup.formRef = { validate: vi.fn(async () => false) }
    await loginSetup.handleSubmit()
    expect(mocks.login).not.toHaveBeenCalled()

    const registerWrapper = mountWithStubs(Register)
    const registerSetup = setupOf(registerWrapper)
    await setInputs(registerWrapper, ['new-user', '13900000000', 'secret1', 'secret1'])
    expect(registerSetup.form).toMatchObject({
      username: 'new-user',
      phone: '13900000000',
      password: 'secret1',
      confirmPassword: 'secret1',
    })
    let confirmError
    registerSetup.form.password = 'a'
    registerSetup.rules.confirmPassword[1].validator(null, 'b', (err) => {
      confirmError = err
    })
    expect(confirmError).toBeInstanceOf(Error)
    registerSetup.rules.confirmPassword[1].validator(null, 'a', (err) => {
      confirmError = err
    })
    expect(confirmError).toBeUndefined()
    registerSetup.formRef = { validate: vi.fn(async () => false) }
    await registerSetup.handleSubmit()

    const forgotWrapper = mountWithStubs(ForgotPassword)
    const forgotSetup = setupOf(forgotWrapper)
    await setInputs(forgotWrapper, ['user@example.com', '123456', 'newpass', 'newpass'])
    expect(forgotSetup.form).toMatchObject({
      account: 'user@example.com',
      code: '123456',
      newPassword: 'newpass',
      confirmPassword: 'newpass',
    })
    const accountResults = []
    forgotSetup.rules.account[0].validator(null, '', (err) => accountResults.push(err?.message))
    forgotSetup.rules.account[0].validator(null, 'bad-email', (err) => accountResults.push(err?.message))
    forgotSetup.rules.account[0].validator(null, 'ok@example.com', (err) => accountResults.push(err))
    expect(accountResults).toEqual(['请输入邮箱', '请输入正确的邮箱', undefined])
    let forgotConfirmError
    forgotSetup.form.newPassword = 'a'
    forgotSetup.rules.confirmPassword[1].validator(null, 'b', (err) => {
      forgotConfirmError = err
    })
    expect(forgotConfirmError).toBeInstanceOf(Error)
    forgotSetup.formRef = {
      validate: vi.fn(async () => false),
      validateField: vi.fn(async () => false),
    }
    await forgotSetup.handleSendCode()
    await forgotSetup.handleSubmit()
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
      account: 'user@example.com',
      code: '123456',
      newPassword: 'abcdef',
      confirmPassword: 'abcdef',
    })

    await setup.handleSendCode()
    await flushPromises()
    expect(mocks.sendVerifyCode).toHaveBeenCalledWith({
      account: 'user@example.com',
      type: 'email',
      purpose: 'reset_password',
    })
    expect(mocks.success).toHaveBeenCalledWith('验证码已发送，请查收邮件')

    await setup.handleSubmit()
    await flushPromises()
    expect(mocks.resetPassword).toHaveBeenCalledWith({
      account: 'user@example.com',
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

  it('covers security dialog template model updates and validation branches', async () => {
    const SecurityDialog = (await import('../UserCenter/components/SecurityDialog.vue')).default
    const wrapper = mountWithStubs(SecurityDialog, {
      modelValue: true,
      profile: { phone: '13811112222', email: 'old@example.com' },
    })
    const setup = setupOf(wrapper)
    const inputs = wrapper.findAllComponents(InputStub)

    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'email')
    expect(setup.tab).toBe('email')
    for (const [index, input] of inputs.entries()) {
      await input.find('input').setValue(`value-${index}`)
    }
    await flushPromises()

    Object.assign(setup.pwdForm, {
      old_password: 'oldpass',
      new_password: 'newpass',
      confirm_password: 'newpass',
    })
    Object.assign(setup.phoneForm, { phone: '13900000000', code: '123456' })
    Object.assign(setup.emailForm, { email: 'new@example.com', code: '654321' })

    let emailError
    setup.emailRules.email[0].validator(null, '', (err) => {
      emailError = err
    })
    expect(emailError).toBeInstanceOf(Error)
    setup.emailRules.email[0].validator(null, 'bad-email', (err) => {
      emailError = err
    })
    expect(emailError).toBeInstanceOf(Error)
    setup.emailRules.email[0].validator(null, 'ok@example.com', (err) => {
      emailError = err
    })
    expect(emailError).toBeUndefined()

    let passwordError
    setup.pwdForm.new_password = 'a'
    setup.pwdRules.confirm_password[1].validator(null, 'b', (err) => {
      passwordError = err
    })
    expect(passwordError).toBeInstanceOf(Error)
    setup.pwdRules.confirm_password[1].validator(null, 'a', (err) => {
      passwordError = err
    })
    expect(passwordError).toBeUndefined()

    setup.pwdRef = { validate: vi.fn(async () => false) }
    await setup.submitPassword()
    setup.phoneForm.phone = '123'
    await setup.sendPhoneCode()
    await setup.bindPhoneSubmit()
    setup.phoneForm.phone = '13900000000'
    setup.phoneForm.code = 'abc'
    await setup.bindPhoneSubmit()
    setup.emailRef = {
      validate: vi.fn(async () => false),
      validateField: vi.fn(async () => false),
    }
    await setup.sendEmailCode()
    await setup.bindEmailSubmit()
    expect(mocks.warning).toHaveBeenCalled()
  })
})
