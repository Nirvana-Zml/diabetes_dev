import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const checkinDate = vi.hoisted(() => {
  const { ref } = require('vue')
  return ref('2026-07-03')
})

const checkinMocks = vi.hoisted(() => ({
  getExercisePresets: vi.fn(async () => [{ exercise_id: 'e1', name: '散步' }]),
  getExerciseRecords: vi.fn(async () => []),
  createExerciseCheckin: vi.fn(async () => ({ points_earned: 10 })),
}))

vi.mock('@/api/checkin', () => checkinMocks)

vi.mock('@/views/CheckinRecords/composables/useCheckinDate', () => {
  const { computed } = require('vue')
  return {
    useCheckinDate: () => ({
      checkinDate,
      isToday: computed(() => checkinDate.value === '2026-07-03'),
      dateDisplay: computed(() => checkinDate.value),
      changeDate: vi.fn(),
      setCheckinDate: (d) => { if (d) checkinDate.value = d },
    }),
  }
})

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

const stubs = {
  CheckinTypeLayout: { template: '<div><slot name="date" /><slot /></div>' },
  CheckinDateBar: { template: '<div />' },
  'el-button': { template: '<button @click="$emit(`click`)"><slot /></button>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-input-number': { template: '<input />' },
  'el-empty': { template: '<div />' },
}

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('ExerciseCheckin error branches', () => {
  it('covers submit catch paths', async () => {
    const { ElMessage } = await import('element-plus')
    const ExerciseCheckin = (await import('../ExerciseCheckin.vue')).default
    const wrapper = shallowMount(ExerciseCheckin, {
      global: { stubs, directives: { loading: {} } },
    })
    await flush()

    wrapper.vm.openExPreset({ exercise_id: 'e1', name: '散步' })
    wrapper.vm.exDialogDuration = 20
    checkinMocks.createExerciseCheckin.mockRejectedValueOnce(new Error('运动失败'))
    await wrapper.vm.submitExPreset()
    expect(ElMessage.error).toHaveBeenCalledWith('运动失败')

    checkinMocks.createExerciseCheckin.mockRejectedValueOnce({})
    wrapper.vm.openExPreset({ exercise_id: 'e1', name: '散步' })
    wrapper.vm.exDialogDuration = 20
    await wrapper.vm.submitExPreset()
    expect(ElMessage.error).toHaveBeenCalledWith('打卡失败')

    wrapper.vm.customEx = { name: '跳绳', calories_per_minute: 8, duration: 10 }
    checkinMocks.createExerciseCheckin.mockRejectedValueOnce(new Error('自定义失败'))
    await wrapper.vm.submitCustomEx()
    expect(ElMessage.error).toHaveBeenCalledWith('自定义失败')

    checkinMocks.createExerciseCheckin.mockRejectedValueOnce({})
    wrapper.vm.customEx = { name: '跳绳', calories_per_minute: 8, duration: 10 }
    await wrapper.vm.submitCustomEx()
    expect(ElMessage.error).toHaveBeenCalledWith('打卡失败')

    wrapper.unmount()
  })

  it('mounts exercise card click and dialog handlers', async () => {
    const ExerciseCheckin = (await import('../ExerciseCheckin.vue')).default
    const wrapper = mount(ExerciseCheckin, {
      global: {
        stubs: {
          CheckinTypeLayout: { template: '<div><slot name="date" /><slot name="header-action" /><slot name="bottom" /><slot /></div>' },
          CheckinDateBar: { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-form': { template: '<div><slot /></div>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-input': defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input />' }),
          'el-input-number': defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input @input="$emit(\'update:modelValue\', 20)" />' }),
          'el-icon': { template: '<i />' },
          Plus: { template: '<i />' },
          'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
        },
        directives: { loading: {} },
      },
    })
    await flush()
    const cards = wrapper.findAll('.ck-ex-card')
    if (cards.length) await cards[0].trigger('click')
    wrapper.vm.exDialogVisible = true
    wrapper.vm.exDialogItem = { exercise_id: 'e1', exercise_name: '散步' }
    wrapper.vm.exDialogDuration = 20
    await flush()
    for (const input of wrapper.findAll('input')) await input.trigger('input')
    const cancel = wrapper.findAll('button').find((b) => b.text().includes('取消'))
    const confirm = wrapper.findAll('button').find((b) => b.text().includes('确认打卡'))
    if (cancel) await cancel.trigger('click')
    wrapper.vm.exDialogVisible = true
    if (confirm) await confirm.trigger('click')
    await wrapper.find('.ck-header-action').trigger('click')
    wrapper.unmount()
  })
})
