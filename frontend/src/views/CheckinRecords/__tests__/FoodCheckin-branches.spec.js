import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const checkinDate = vi.hoisted(() => {
  const { ref } = require('vue')
  return ref('2026-07-03')
})

const checkinMocks = vi.hoisted(() => ({
  getFoodCategories: vi.fn(async () => [
    { category_id: 'cat_grain', category_name: '主食' },
    { category_id: 'cat_custom', category_name: '自定义' },
  ]),
  getFoodPresets: vi.fn(async (categoryId) => (categoryId === 'cat_custom'
    ? [{ food_id: 'uf1', food_name: '自定义面包', category_id: 'cat_custom', is_user_custom: true, original_category_id: 'cat_grain', calories_per_gram: 2.5, is_liquid: false, ml_to_g_ratio: 1, image_object_key: 'food/custom.jpg' }]
    : [{ food_id: 'food_rice_001', food_name: '糙米饭', category_id: 'cat_grain', calories_per_gram: 1.16, is_liquid: false, ml_to_g_ratio: 1, image_object_key: 'food/rice.jpg' }])),
  getFoodRecords: vi.fn(async () => []),
  uploadCheckinImage: vi.fn(async () => ({ object_key: 'food/upload.jpg', image_url: 'food.png' })),
  recognizeFoodImage: vi.fn(async () => ({
    food_name: '糙米饭',
    category_id: 'cat_grain',
    calories_per_gram: 1.16,
    is_liquid: false,
    ml_to_g_ratio: 1,
    suggested_input_amount: 150,
    suggested_input_unit: 1,
    recognition_summary: '识别为糙米饭',
    nutrition_tip: '适量食用',
    matched_food_id: 'food_rice_001',
    source_type: 1,
    has_error: false,
  })),
  createFoodCheckin: vi.fn(async () => ({ points_earned: 10 })),
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
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve()),
  },
}))

import { ElMessage, ElMessageBox } from 'element-plus'

const stubs = {
  CheckinTypeLayout: { template: '<div><slot name="date" /><slot name="header-action" /><slot /></div>' },
  CheckinDateBar: { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input type="number" />' },
  'el-button': { template: '<button @click="$emit(`click`)"><slot /></button>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-time-picker': { template: '<input />' },
  'el-icon': { template: '<i />' },
  Plus: { template: '<i />' },
  Camera: { template: '<i />' },
}

async function flush() {
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function mountFood() {
  setActivePinia(createPinia())
  return shallowMount(FoodCheckin, { global: { stubs, directives: { loading: {} } } })
}

let FoodCheckin

beforeEach(() => {
  vi.clearAllMocks()
})

describe('FoodCheckin recognition branches', () => {
  beforeAll(async () => {
    FoodCheckin = (await import('../FoodCheckin.vue')).default
  }, 30000)

  it('covers computed hints and category selection', async () => {
    const wrapper = mountFood()
    await flush()
    expect(wrapper.vm.selectableFoodCategories.every((c) => c.category_id !== 'cat_custom')).toBe(true)
    expect(wrapper.vm.emptyPresetHint).toContain('暂无预设食物')
    wrapper.vm.selectedCategoryId = 'cat_custom'
    expect(wrapper.vm.isCustomCategory).toBe(true)
    expect(wrapper.vm.emptyPresetHint).toContain('自定义')
    await wrapper.vm.selectCategory('cat_grain')
    expect(checkinMocks.getFoodPresets).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('covers openFoodPreset liquid and solid defaults', async () => {
    const wrapper = mountFood()
    await flush()
    wrapper.vm.openFoodPreset({ food_id: 'f1', is_liquid: true, ml_to_g_ratio: 1.03 })
    expect(wrapper.vm.foodDialogAmount).toBe(200)
    expect(wrapper.vm.foodDialogUnit).toBe(2)
    wrapper.vm.openFoodPreset({ food_id: 'f2', is_liquid: false })
    expect(wrapper.vm.foodDialogAmount).toBe(100)
    expect(wrapper.vm.foodDialogUnit).toBe(1)
    wrapper.unmount()
  })

  it('covers recognition normalization and preset switch confirm', async () => {
    const wrapper = mountFood()
    await flush()
    const target = wrapper.vm.customFood
    target.image_object_key = 'food/img.jpg'
    await wrapper.vm.runFoodRecognition()
    expect(target.name).toBe('糙米饭')
    expect(target.ai_summary).toContain('糙米饭')

    target.matched_food_id = 'food_rice_001'
    target.ai_source_type = 1
    wrapper.vm.foodPresets = [{ food_id: 'food_rice_001', food_name: '糙米饭', is_liquid: false }]
    await wrapper.vm.applyRecognitionResult(target, {
      foodName: '牛奶',
      categoryId: 'cat_drink',
      caloriesPerGram: 0.54,
      isLiquid: true,
      mlToGRatio: 1.03,
      suggestedInputAmount: 200,
      suggestedInputUnit: 2,
      matchedFoodId: 'food_milk_001',
      sourceType: 1,
      nutritionTip: '补钙',
      recognitionSummary: '识别为牛奶',
      hasError: false,
    })
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(wrapper.vm.foodDialogVisible).toBe(true)

    await wrapper.vm.applyRecognitionResult(target, { has_error: true, error_message: '无法识别' })
    expect(ElMessage.warning).toHaveBeenCalledWith('无法识别')

    expect(wrapper.vm.normalizeRecognitionPayload([{ food_name: '数组项' }])).toMatchObject({ food_name: '数组项' })
    expect(wrapper.vm.normalizeRecognitionPayload(null)).toEqual({})
    expect(wrapper.vm.toBool('true')).toBe(true)
    expect(wrapper.vm.toBool(0)).toBe(false)
    wrapper.unmount()
  })

  it('covers recognition error paths and matched preset reload', async () => {
    const wrapper = mountFood()
    await flush()
    const target = wrapper.vm.customFood
    target.image_object_key = ''
    await wrapper.vm.runFoodRecognition()
    expect(ElMessage.warning).toHaveBeenCalledWith('请先上传图片')

    target.image_object_key = 'food/img.jpg'
    checkinMocks.recognizeFoodImage.mockRejectedValueOnce(new Error('识别超时'))
    await wrapper.vm.runFoodRecognition()
    expect(ElMessage.error).toHaveBeenCalledWith('识别超时')

    wrapper.vm.foodPresets = []
    ElMessageBox.confirm.mockImplementationOnce(() => Promise.resolve())
    await wrapper.vm.applyRecognitionResult(target, {
      matched_food_id: 'missing',
      source_type: 1,
      category_id: 'cat_grain',
      food_name: '饭',
      has_error: false,
    })
    expect(checkinMocks.getFoodPresets).toHaveBeenCalled()

    ElMessageBox.confirm.mockImplementationOnce(() => Promise.reject(new Error('cancel')))
    await wrapper.vm.applyRecognitionResult(target, {
      matched_food_id: 'food_rice_001',
      source_type: 1,
      food_name: '饭',
      has_error: false,
    })
    wrapper.unmount()
  })

  it('covers custom food submit for user preset branch', async () => {
    const wrapper = mountFood()
    await flush()
    wrapper.vm.foodDialogFood = {
      food_id: 'uf1',
      food_name: '自定义面包',
      is_user_custom: true,
      original_category_id: 'cat_grain',
      calories_per_gram: 2.5,
      is_liquid: true,
      ml_to_g_ratio: 1.03,
      image_object_key: 'food/custom.jpg',
    }
    wrapper.vm.foodDialogAmount = 150
    wrapper.vm.foodDialogTime = '08:30'
    wrapper.vm.foodDialogMealPeriod = 1
    wrapper.vm.foodDialogUnit = 2
    await wrapper.vm.submitFoodPreset()
    expect(checkinMocks.createFoodCheckin).toHaveBeenCalledWith(expect.objectContaining({
      source_type: 2,
      food_name: '自定义面包',
      input_unit: 2,
    }))
    wrapper.unmount()
  })

  it('covers onFoodFileChange and handleUpload with recognition', async () => {
    const wrapper = mountFood()
    await flush()
    const event = { target: { files: [new File(['food'], 'food.png', { type: 'image/png' })], value: 'x' } }
    await wrapper.vm.onFoodFileChange(event)
    expect(event.target.value).toBe('')
    expect(checkinMocks.uploadCheckinImage).toHaveBeenCalled()
    expect(checkinMocks.recognizeFoodImage).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('covers preset submit system branch, upload error and custom food flow', async () => {
    const wrapper = mountFood()
    await flush()

    wrapper.vm.foodDialogFood = {
      food_id: 'food_rice_001',
      food_name: '糙米饭',
      is_user_custom: false,
      is_liquid: false,
      ml_to_g_ratio: 1,
      image_object_key: 'food/rice.jpg',
    }
    wrapper.vm.foodDialogAmount = 120
    wrapper.vm.foodDialogTime = '08:30'
    wrapper.vm.foodDialogMealPeriod = 1
    wrapper.vm.foodDialogUnit = 1
    await wrapper.vm.submitFoodPreset()
    expect(checkinMocks.createFoodCheckin).toHaveBeenCalledWith(expect.objectContaining({
      source_type: 1,
      food_id: 'food_rice_001',
    }))

    checkinMocks.createFoodCheckin.mockRejectedValueOnce(new Error('提交失败'))
    await wrapper.vm.submitFoodPreset()
    expect(ElMessage.error).toHaveBeenCalledWith('提交失败')

    checkinMocks.uploadCheckinImage.mockRejectedValueOnce(new Error('上传失败'))
    const target = wrapper.vm.customFood
    await wrapper.vm.handleUpload(new File(['x'], 'x.png', { type: 'image/png' }), target)
    expect(ElMessage.error).toHaveBeenCalledWith('上传失败')

    wrapper.vm.customFood = {
      category_id: 'cat_grain',
      name: '鸡蛋',
      image_object_key: 'img',
      input_amount: 100,
      calories_per_gram: 1,
      is_liquid: false,
      ml_to_g_ratio: 1,
      input_unit: 1,
      meal_period: 1,
      record_time: '09:00',
    }
    checkinMocks.createFoodCheckin.mockResolvedValueOnce({ points_earned: 10 })
    await wrapper.vm.submitCustomFood()
    expect(wrapper.vm.customFood.name).toBe('')

    checkinMocks.getFoodRecords.mockClear()
    checkinDate.value = '2026-06-15'
    await flush()
    expect(checkinMocks.getFoodRecords).toHaveBeenCalledWith('2026-06-15')

    wrapper.vm.foodRecords = [{ total_calories: 500 }]
    expect(wrapper.vm.foodDailyTotalCalories).toBe(500)
    expect(wrapper.vm.foodCaloriePercent).toBeGreaterThan(0)

    wrapper.unmount()
  })

  it('covers recognition payload normalization and applyRecognition branches', async () => {
    const wrapper = mountFood()
    await flush()
    const target = wrapper.vm.customFood

    expect(wrapper.vm.normalizeRecognitionPayload(null)).toEqual({})
    expect(wrapper.vm.normalizeRecognitionPayload([{ foodName: '数组' }])).toMatchObject({ food_name: '数组' })
    expect(wrapper.vm.normalizeRecognitionPayload({
      foodName: '牛奶',
      categoryId: 'cat',
      caloriesPerGram: 0.5,
      isLiquid: true,
      mlToGRatio: 1.1,
      suggestedInputAmount: 200,
      suggestedInputUnit: 2,
      matchedFoodId: 'f1',
      sourceType: 1,
      nutritionTip: 'tip',
      recognitionSummary: 'sum',
      hasError: false,
      errorMessage: 'err',
    })).toMatchObject({ food_name: '牛奶', category_id: 'cat' })

    expect(wrapper.vm.toBool(1)).toBe(true)
    expect(wrapper.vm.toBool('false')).toBe(false)

    await wrapper.vm.applyRecognitionResult(target, { has_error: true })
    expect(ElMessage.warning).toHaveBeenCalledWith('未能识别食物，请手动填写')

    wrapper.vm.foodPresets = [{ food_id: 'f9', food_name: '预设', is_liquid: false }]
    ElMessageBox.confirm.mockImplementationOnce(() => Promise.resolve())
    await wrapper.vm.applyRecognitionResult(target, {
      food_name: '饭',
      matched_food_id: 'f9',
      source_type: 1,
      category_id: 'cat_grain',
    })
    expect(wrapper.vm.foodDialogVisible).toBe(true)

    ElMessageBox.confirm.mockImplementationOnce(() => Promise.resolve())
    wrapper.vm.foodPresets = []
    checkinMocks.getFoodPresets.mockResolvedValueOnce([{ food_id: 'f10', food_name: '加载预设', is_liquid: false }])
    await wrapper.vm.applyRecognitionResult(target, {
      food_name: '饭',
      matched_food_id: 'f10',
      source_type: 1,
      category_id: 'cat_grain',
    })

    await wrapper.vm.handleUpload(null, target)
    wrapper.unmount()
  })

  it('covers remaining validation, fallback and branch paths', async () => {
    const wrapper = mountFood()
    await flush()

    await wrapper.vm.loadCategories()
    expect(wrapper.vm.foodCategories.length).toBeGreaterThan(0)
    await wrapper.vm.loadCategories()

    wrapper.vm.foodRecords = [{ total_calories: null }]
    expect(wrapper.vm.foodDailyTotalCalories).toBe(0)

    checkinMocks.uploadCheckinImage.mockRejectedValueOnce({})
    await wrapper.vm.handleUpload(new File(['x'], 'x.png'), wrapper.vm.customFood)

    const target = wrapper.vm.customFood
    await wrapper.vm.applyRecognitionResult(target, {
      food_name: '饭',
      recognition_summary: '识别',
      has_error: false,
    })

    ElMessageBox.confirm.mockImplementationOnce(() => Promise.resolve())
    wrapper.vm.foodPresets = []
    await wrapper.vm.applyRecognitionResult(target, {
      food_name: '饭',
      matched_food_id: 'missing_id',
      source_type: 1,
      category_id: 'cat_grain',
      has_error: false,
    })

    checkinMocks.recognizeFoodImage.mockRejectedValueOnce({})
    target.image_object_key = 'img'
    await wrapper.vm.runFoodRecognition()

    wrapper.vm.foodDialogFood = { food_id: 'f1', food_name: '固体', is_user_custom: false, is_liquid: false, ml_to_g_ratio: 1, image_object_key: 'k' }
    wrapper.vm.foodDialogAmount = 100
    wrapper.vm.foodDialogTime = '09:00'
    wrapper.vm.foodDialogUnit = 1
    await wrapper.vm.submitFoodPreset()

    checkinMocks.createFoodCheckin.mockRejectedValueOnce({})
    await wrapper.vm.submitFoodPreset()

    wrapper.vm.customFood.category_id = ''
    await wrapper.vm.submitCustomFood()
    wrapper.vm.customFood.category_id = 'cat_grain'
    wrapper.vm.customFood.name = '  '
    await wrapper.vm.submitCustomFood()

    checkinMocks.createFoodCheckin.mockRejectedValueOnce({})
    wrapper.vm.customFood = {
      category_id: 'cat_grain', name: '蛋', image_object_key: 'k', input_amount: 50,
      record_time: '09:00', meal_period: 1, calories_per_gram: 1, is_liquid: false,
      ml_to_g_ratio: 1, input_unit: 1,
    }
    await wrapper.vm.submitCustomFood()

    wrapper.unmount()
  })

  it('mounts template handlers for mode toggle, chips, cards and dialog', async () => {
    checkinMocks.getFoodPresets.mockResolvedValueOnce([{
      food_id: 'food_rice_001', food_name: '糙米饭', category_id: 'cat_grain',
      calories_per_gram: 1.16, is_liquid: false, image_url: 'bad.jpg', image_object_key: 'k',
    }])
    const wrapper = mount(FoodCheckin, {
      global: {
        stubs: {
          CheckinTypeLayout: { template: '<div><slot name="date" /><slot name="header-action" /><slot /></div>' },
          CheckinDateBar: { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-form': { template: '<div><slot /></div>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-icon': { template: '<i />' },
          Plus: { template: '<i />' },
          Camera: { template: '<i />' },
          'el-switch': { template: '<input />' },
          'el-input': { template: '<input />' },
          'el-input-number': { template: '<input />' },
          'el-select': defineComponent({
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<div @click="$emit(\'update:modelValue\', modelValue)"><slot /></div>',
          }),
          'el-option': { template: '<option />' },
          'el-time-picker': defineComponent({
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<input @input="$emit(\'update:modelValue\', \'08:30\')" />',
          }),
          'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          'el-dialog': {
            props: ['modelValue'],
            emits: ['update:modelValue'],
            template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
          },
        },
        directives: { loading: {} },
      },
    })
    await flush()

    await wrapper.find('.ck-header-action').trigger('click')
    await wrapper.find('.ck-header-action').trigger('click')
    const chips = wrapper.findAll('.ck-chip')
    if (chips.length) await chips[0].trigger('click')
    const cards = wrapper.findAll('.ck-food-card')
    if (cards.length) await cards[0].trigger('click')
    for (const img of wrapper.findAll('img')) await img.trigger('error')
    await wrapper.find('.ck-header-action').trigger('click')
    await wrapper.find('.upload-box').trigger('click')

    wrapper.vm.foodDialogVisible = true
    wrapper.vm.foodDialogFood = { food_id: 'f1', food_name: '饭', is_liquid: true, ml_to_g_ratio: 1, calories_per_gram: 1 }
    wrapper.vm.foodDialogAmount = 100
    wrapper.vm.foodDialogTime = '08:30'
    await flush()
    for (const sel of wrapper.findAll('.el-select, [class*="el-select"]')) {
      try { await sel.trigger('click') } catch { /* stub may differ */ }
    }
    const btns = wrapper.findAll('button')
    const cancel = btns.find((b) => b.text().includes('取消'))
    const confirm = btns.find((b) => b.text().includes('确认打卡'))
    if (cancel) await cancel.trigger('click')
    wrapper.vm.foodDialogVisible = true
    if (confirm) await confirm.trigger('click')

    wrapper.unmount()
  })
})
