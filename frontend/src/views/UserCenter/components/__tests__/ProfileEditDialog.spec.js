import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

const mocks = vi.hoisted(() => ({
  updateUserProfile: vi.fn(),
  uploadUserAvatar: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  updateUserProfile: mocks.updateUserProfile,
  uploadUserAvatar: mocks.uploadUserAvatar,
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.success,
    error: mocks.error,
    warning: mocks.warning,
  },
}))

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': defineComponent({
    setup(_props, { slots, expose }) {
      expose({ validate: vi.fn(() => Promise.resolve()) })
      return () => h('form', slots.default?.())
    },
  }),
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': defineComponent({
    name: 'ElInput',
    emits: ['update:modelValue'],
    template: '<input />',
  }),
  'el-radio-group': defineComponent({
    name: 'ElRadioGroup',
    emits: ['update:modelValue'],
    template: '<div><slot /></div>',
  }),
  'el-radio': { template: '<label><slot /></label>' },
  'el-date-picker': defineComponent({
    name: 'ElDatePicker',
    emits: ['update:modelValue'],
    template: '<input />',
  }),
  'el-button': defineComponent({
    name: 'ElButton',
    emits: ['click'],
    template: '<button @click="$emit(`click`)"><slot /></button>',
  }),
  'el-avatar': { template: '<div />' },
}

function setupOf(wrapper) {
  return wrapper.vm.$.setupState
}

function validForm() {
  return {
    validate: vi.fn(() => Promise.resolve()),
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ProfileEditDialog interactions without changing component source', () => {
  it('uploads avatar and submits profile changes', async () => {
    const ProfileEditDialog = (await import('../ProfileEditDialog.vue')).default
    mocks.uploadUserAvatar.mockResolvedValue({ avatar_url: 'avatar.png' })
    mocks.updateUserProfile.mockResolvedValue({ nickname: '新昵称' })

    const wrapper = mount(ProfileEditDialog, {
      props: {
        modelValue: true,
        profile: { nickname: '旧昵称', gender: 'male', birth_date: '2000-01-01' },
      },
      global: { stubs },
    })
    const setup = setupOf(wrapper)
    setup.formRef = validForm()
    setup.fileRef = { click: vi.fn(), value: 'old' }
    setup.form = { nickname: '新昵称', gender: 'female', birth_date: '2001-02-03' }

    setup.handleAvatarUpload()
    expect(setup.fileRef.click).toHaveBeenCalled()

    await setup.onFileChange({ target: { files: [new File(['x'], 'avatar.png', { type: 'image/png' })] } })
    await flushPromises()
    expect(mocks.uploadUserAvatar).toHaveBeenCalled()
    expect(setup.form.avatar_url).toBe('avatar.png')
    expect(setup.fileRef.value).toBe('')

    await setup.submit()
    await flushPromises()
    expect(mocks.updateUserProfile).toHaveBeenCalledWith({
      nickname: '新昵称',
      gender: 'female',
      birth_date: '2001-02-03',
    })
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  })

  it('handles avatar validation and upload failure branches', async () => {
    const ProfileEditDialog = (await import('../ProfileEditDialog.vue')).default
    const wrapper = mount(ProfileEditDialog, {
      props: { modelValue: true, profile: {} },
      global: { stubs },
    })
    const setup = setupOf(wrapper)
    setup.fileRef = { value: 'old' }

    await setup.onFileChange({ target: { files: [] } })
    expect(mocks.uploadUserAvatar).not.toHaveBeenCalled()

    await setup.onFileChange({ target: { files: [new File(['x'.repeat(3 * 1024 * 1024)], 'big.png')] } })
    expect(mocks.warning).toHaveBeenCalledWith('图片大小不能超过 2MB')

    mocks.uploadUserAvatar.mockRejectedValueOnce(new Error('上传失败'))
    await setup.onFileChange({ target: { files: [new File(['x'], 'small.png')] } })
    await flushPromises()
    expect(mocks.error).toHaveBeenCalledWith('上传失败')
    expect(setup.uploading).toBe(false)
  })

  it('syncs profile when dialog opens', async () => {
    const ProfileEditDialog = (await import('../ProfileEditDialog.vue')).default
    const wrapper = mount(ProfileEditDialog, {
      props: {
        modelValue: false,
        profile: { nickname: '待编辑', gender: 'female' },
      },
      global: { stubs },
    })

    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(setupOf(wrapper).form).toMatchObject({ nickname: '待编辑', gender: 'female' })

    for (const input of wrapper.findAllComponents({ name: 'ElInput' })) {
      input.vm.$emit('update:modelValue', 'updated')
    }
    for (const group of wrapper.findAllComponents({ name: 'ElRadioGroup' })) {
      group.vm.$emit('update:modelValue', 'male')
    }
    for (const picker of wrapper.findAllComponents({ name: 'ElDatePicker' })) {
      picker.vm.$emit('update:modelValue', '2001-01-01')
    }
    for (const button of wrapper.findAllComponents({ name: 'ElButton' })) {
      button.vm.$emit('click')
    }
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  })
})
