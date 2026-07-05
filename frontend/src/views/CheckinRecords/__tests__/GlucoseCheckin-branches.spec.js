import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const checkinDate = vi.hoisted(() => {
  const { ref } = require('vue')
  return ref('2026-07-03')
})

const checkinMocks = vi.hoisted(() => ({
  getGlucoseRecords: vi.fn(async () => []),
  getGlucoseHistory: vi.fn(async () => ({ records: [], summary: {} })),
  createGlucoseCheckin: vi.fn(async () => ({ points_earned: 15 })),
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
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
}))

vi.mock('echarts', () => ({
  init: vi.fn(() => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() })),
}))

const stubs = {
  CheckinTypeLayout: { template: '<div><slot name="date" /><slot /></div>' },
  CheckinDateBar: { template: '<div />' },
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input-number': { template: '<input />' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-button': { template: '<button @click="$emit(`click`)"><slot /></button>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
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
  checkinDate.value = '2026-07-03'
})

describe('GlucoseCheckin date watch', () => {
  it('reloads records and history when checkinDate changes', async () => {
    checkinDate.value = '2026-07-03'
    const GlucoseCheckin = (await import('../GlucoseCheckin.vue')).default

    const wrapper = shallowMount(GlucoseCheckin, {
      global: { stubs, directives: { loading: {} } },
    })
    await flush()

    const recordsCalls = checkinMocks.getGlucoseRecords.mock.calls.length
    const historyCalls = checkinMocks.getGlucoseHistory.mock.calls.length

    checkinDate.value = '2026-06-01'
    await flush()
    await flush()

    expect(checkinMocks.getGlucoseRecords.mock.calls.length).toBeGreaterThan(recordsCalls)
    expect(checkinMocks.getGlucoseHistory.mock.calls.length).toBeGreaterThan(historyCalls)
    expect(checkinMocks.getGlucoseRecords).toHaveBeenLastCalledWith('2026-06-01')

    wrapper.unmount()
  }, 15000)

  it('covers chart formatters, submit branches and empty date guard', async () => {
    const { ElMessage, ElMessageBox } = await import('element-plus')
    const echarts = await import('echarts')
    const GlucoseCheckin = (await import('../GlucoseCheckin.vue')).default
    const wrapper = shallowMount(GlucoseCheckin, {
      global: { stubs, directives: { loading: {} } },
    })
    await flush()

    wrapper.vm.glucoseChartRef = document.createElement('div')
    wrapper.vm.glucoseHistory = {
      records: [{ glucose_value: 6.2, record_time: '2026-07-03 08:00:00', measure_context_label: '空腹', checkin_date: '2026-07-03' }],
      summary: {},
    }
    wrapper.vm.renderGlucoseChart()
    const chart = echarts.init.mock.results.at(-1).value
    const opts = chart.setOption.mock.calls.at(-1)[0]
    expect(opts.tooltip.formatter([{ dataIndex: 0 }])).toContain('mmol/L')
    expect(opts.yAxis.min({ min: 3.5 })).toBe(2)
    expect(opts.yAxis.max({ max: 7.5 })).toBe(9)

    wrapper.vm.glucoseForm = { value: null, context: 4 }
    await wrapper.vm.submitGlucose()
    wrapper.vm.glucoseForm = { value: 25, context: 4 }
    ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    await wrapper.vm.submitGlucose()
    wrapper.vm.glucoseForm = { value: 6.0, context: 4 }
    checkinMocks.createGlucoseCheckin.mockRejectedValueOnce(new Error('提交失败'))
    await wrapper.vm.submitGlucose()
    expect(ElMessage.error).toHaveBeenCalledWith('提交失败')

    checkinMocks.getGlucoseHistory.mockRejectedValueOnce({})
    await wrapper.vm.loadGlucoseHistory()
    expect(ElMessage.error).toHaveBeenCalledWith('加载血糖趋势失败')

    wrapper.vm.glucoseHistory = { records: [], summary: null }
    expect(wrapper.vm.glucoseSummary).toEqual({})

    wrapper.vm.glucoseHistory = {
      records: Array.from({ length: 10 }, (_, i) => ({
        glucose_value: 5 + i * 0.1,
        record_time: `2026-07-${String(i + 1).padStart(2, '0')} 08:00:00`,
        measure_context_label: '空腹',
        checkin_date: `2026-07-${String(i + 1).padStart(2, '0')}`,
      })),
      summary: { count: 10, avg: 5.5 },
    }
    wrapper.vm.renderGlucoseChart()
    const chartMany = echarts.init.mock.results.at(-1).value
    const optsMany = chartMany.setOption.mock.calls.at(-1)[0]
    expect(optsMany.xAxis.axisLabel.rotate).toBe(35)
    expect(optsMany.yAxis.min({ min: 3.5 })).toBe(2)
    expect(optsMany.yAxis.max({ max: 7.5 })).toBe(9)
    expect(optsMany.tooltip.formatter([])).toBe('')

    wrapper.vm.glucoseHistory = { records: [], summary: {} }
    wrapper.vm.renderGlucoseChart()
    const chartEmpty = echarts.init.mock.results.at(-1).value
    const optsEmpty = chartEmpty.setOption.mock.calls.at(-1)[0]
    expect(optsEmpty.yAxis.min).toBe(0)
    expect(optsEmpty.yAxis.max).toBe(12)

    wrapper.vm.glucoseHistory = { records: [{ checkin_date: '2026-07-01' }], summary: {} }
    wrapper.vm.renderGlucoseChart()
    const chartNoTime = echarts.init.mock.results.at(-1).value
    const optsNoTime = chartNoTime.setOption.mock.calls.at(-1)[0]
    expect(optsNoTime.xAxis.data[0]).toBe('2026-07-01')

    ElMessageBox.confirm.mockResolvedValueOnce(undefined)
    wrapper.vm.glucoseForm = { value: 25, context: 4 }
    checkinMocks.createGlucoseCheckin.mockResolvedValueOnce({})
    await wrapper.vm.submitGlucose()
    expect(ElMessage.success).toHaveBeenCalledWith('打卡成功！+15 积分')

    checkinMocks.createGlucoseCheckin.mockRejectedValueOnce({})
    wrapper.vm.glucoseForm = { value: 6.0, context: 4 }
    await wrapper.vm.submitGlucose()
    expect(ElMessage.error).toHaveBeenCalledWith('打卡失败')

    wrapper.unmount()
  })

  it('mounts glucose form v-model and radio group handlers', async () => {
    const GlucoseCheckin = (await import('../GlucoseCheckin.vue')).default
    const wrapper = mount(GlucoseCheckin, {
      global: {
        stubs: {
          CheckinTypeLayout: { template: '<div><slot name="date" /><slot name="bottom" /><slot /></div>' },
          CheckinDateBar: { template: '<div />' },
          'el-form': { template: '<div><slot /></div>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-input-number': defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input @input="$emit(\'update:modelValue\', 6)" />' }),
          'el-select': defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<div @click="$emit(\'update:modelValue\', 2)" />' }),
          'el-option': { template: '<option />' },
          'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-radio-group': defineComponent({
            name: 'ElRadioGroup',
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<div @click="$emit(\'update:modelValue\', 7)"><slot /></div>',
          }),
          'el-radio-button': { template: '<span />', props: ['value'] },
        },
        directives: { loading: {} },
      },
    })
    await flush()
    wrapper.vm.glucoseChartRef = document.createElement('div')
    for (const el of wrapper.findAll('input')) await el.trigger('input')
    const radio = wrapper.findComponent({ name: 'ElRadioGroup' })
    if (radio.exists()) await radio.trigger('click')
    wrapper.unmount()
  })
})
