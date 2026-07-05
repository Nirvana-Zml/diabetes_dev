import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const checkinDate = vi.hoisted(() => {
  const { ref } = require('vue')
  return ref('2026-07-03')
})

const checkinMocks = vi.hoisted(() => ({
  getMedicationPresets: vi.fn(async () => [
    { drug_id: 'm1', drug_name: '二甲双胍', is_user_custom: false },
    { drug_id: 'm2', drug_name: '自定义药', is_user_custom: true },
  ]),
  getMedicationRecords: vi.fn(async () => []),
  createMedicationCheckin: vi.fn(async () => ({ points_earned: 10 })),
  uploadCheckinImage: vi.fn(async () => ({ object_key: 'k1', image_url: 'u.png' })),
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
  CheckinTypeLayout: { template: '<div><slot name="date" /><slot name="header-action" /><slot /></div>' },
  CheckinDateBar: { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-button': { template: '<button @click="$emit(`click`)"><slot /></button>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-switch': { template: '<input type="checkbox" />' },
  'el-icon': { template: '<i />' },
  Plus: { template: '<i />' },
}

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  checkinDate.value = '2026-07-03'
})

describe('MedicationCheckin preset filters', () => {
  it('covers filteredMedicationPresets and medEmptyHint for both tabs', async () => {
    const MedicationCheckin = (await import('../MedicationCheckin.vue')).default
    const wrapper = shallowMount(MedicationCheckin, {
      global: { stubs, directives: { loading: {} } },
    })
    await flush()

    expect(wrapper.vm.filteredMedicationPresets).toHaveLength(1)
    expect(wrapper.vm.filteredMedicationPresets[0].drug_id).toBe('m1')
    expect(wrapper.vm.medEmptyHint).toContain('预设')

    wrapper.vm.medPresetTab = 'custom'
    await flush()
    expect(wrapper.vm.filteredMedicationPresets).toHaveLength(1)
    expect(wrapper.vm.filteredMedicationPresets[0].drug_id).toBe('m2')
    expect(wrapper.vm.medEmptyHint).toContain('自定义')

    wrapper.vm.medicationPresets = []
    wrapper.vm.medPresetTab = 'system'
    expect(wrapper.vm.medEmptyHint).toContain('预设')
    wrapper.vm.medPresetTab = 'custom'
    expect(wrapper.vm.medEmptyHint).toContain('自定义')

    wrapper.unmount()
  }, 15000)

  it('covers openMedPreset, upload, preset/custom submit and error paths', async () => {
    const { ElMessage } = await import('element-plus')
    const MedicationCheckin = (await import('../MedicationCheckin.vue')).default
    const wrapper = shallowMount(MedicationCheckin, {
      global: { stubs, directives: { loading: {} } },
    })
    await flush()

    wrapper.vm.openMedPreset({ drug_id: 'm1', drug_name: '二甲双胍', default_dosage: '1片' })
    expect(wrapper.vm.medDialogVisible).toBe(true)
    expect(wrapper.vm.medDialogDosage).toBe('1片')

    await wrapper.vm.handleUpload(null, wrapper.vm.customMed)
    checkinMocks.uploadCheckinImage.mockResolvedValueOnce({ object_key: 'k2', image_url: 'u2.png' })
    await wrapper.vm.handleUpload(new File(['m'], 'm.png', { type: 'image/png' }), wrapper.vm.customMed)
    checkinMocks.uploadCheckinImage.mockRejectedValueOnce(new Error('上传失败'))
    await wrapper.vm.handleUpload(new File(['m'], 'm.png', { type: 'image/png' }), wrapper.vm.customMed)

    const event = { target: { files: [new File(['m'], 'm.png', { type: 'image/png' })], value: 'x' } }
    await wrapper.vm.onMedFileChange(event)
    expect(event.target.value).toBe('')

    wrapper.vm.openMedPreset({ drug_id: 'm1', drug_name: '二甲双胍' })
    wrapper.vm.medDialogDosage = '1片'
    checkinMocks.createMedicationCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await wrapper.vm.submitMedPreset()

    wrapper.vm.openMedPreset({ is_user_custom: true, drug_name: '自定义' })
    wrapper.vm.medDialogDosage = '2片'
    checkinMocks.createMedicationCheckin.mockRejectedValueOnce(new Error('打卡失败'))
    await wrapper.vm.submitMedPreset()

    wrapper.vm.customMed = { name: '药', dosage: '1片', taken: true, image_object_key: 'k1' }
    checkinMocks.createMedicationCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await wrapper.vm.submitCustomMed()
    checkinMocks.createMedicationCheckin.mockRejectedValueOnce(new Error('自定义失败'))
    wrapper.vm.customMed = { name: '药', dosage: '1片', taken: false, image_object_key: 'k1' }
    await wrapper.vm.submitCustomMed()

    const { useCheckinDate } = await import('@/views/CheckinRecords/composables/useCheckinDate')
    const { setCheckinDate } = useCheckinDate()
    setCheckinDate('')
    await wrapper.vm.loadRecords()
    setCheckinDate('2026-07-03')
    await wrapper.vm.loadRecords()

    expect(ElMessage.error).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('covers med preset submit error fallbacks and header tab branches', async () => {
    const { ElMessage } = await import('element-plus')
    const MedicationCheckin = (await import('../MedicationCheckin.vue')).default
    const wrapper = shallowMount(MedicationCheckin, {
      global: { stubs, directives: { loading: {} } },
    })
    await flush()

    wrapper.vm.openMedPreset({ drug_id: 'm1', drug_name: '药' })
    wrapper.vm.medDialogDosage = '1片'
    checkinMocks.createMedicationCheckin.mockRejectedValueOnce({})
    await wrapper.vm.submitMedPreset()
    expect(ElMessage.error).toHaveBeenCalledWith('打卡失败')

    wrapper.vm.customMed = { name: '药', dosage: '1', taken: true, image_object_key: 'k1' }
    checkinMocks.createMedicationCheckin.mockRejectedValueOnce({})
    await wrapper.vm.submitCustomMed()
    expect(ElMessage.error).toHaveBeenCalledWith('打卡失败')

    wrapper.vm.medMode = 'custom'
    await flush()
    wrapper.vm.medMode = 'preset'
    await flush()

    wrapper.unmount()
  })

  it('mounts template click handlers and dialog v-model', async () => {
    const MedicationCheckin = (await import('../MedicationCheckin.vue')).default
    const wrapper = mount(MedicationCheckin, {
      global: {
        stubs: {
          CheckinTypeLayout: { template: '<div><slot name="date" /><slot name="header-action" /><slot name="bottom" /><slot /></div>' },
          CheckinDateBar: { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-form': { template: '<div><slot /></div>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-input': defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input />' }),
          'el-switch': defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input />' }),
          'el-tag': { template: '<span />' },
          'el-icon': { template: '<i />' },
          Plus: { template: '<i />' },
          Camera: { template: '<i />' },
          'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
        },
        directives: { loading: {} },
      },
    })
    await flush()
    await wrapper.find('.ck-header-action').trigger('click')
    await wrapper.find('.ck-header-action').trigger('click')
    for (const tab of wrapper.findAll('.ck-chip')) await tab.trigger('click')
    const cards = wrapper.findAll('.ck-med-card')
    if (cards.length) await cards[0].trigger('click')
    for (const img of wrapper.findAll('img')) await img.trigger('error')
    await wrapper.find('.ck-header-action').trigger('click')
    await wrapper.find('.upload-box').trigger('click')
    wrapper.vm.medDialogVisible = true
    wrapper.vm.medDialogDrug = { drug_id: 'm1', drug_name: '药' }
    await flush()
    const cancel = wrapper.findAll('button').find((b) => b.text().includes('取消'))
    const confirm = wrapper.findAll('button').find((b) => b.text().includes('确认打卡'))
    if (cancel) await cancel.trigger('click')
    wrapper.vm.medDialogVisible = true
    if (confirm) await confirm.trigger('click')
    wrapper.unmount()
  })
})
