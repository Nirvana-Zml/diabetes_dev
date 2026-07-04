<template>
  <CheckinTypeLayout title="食物打卡">
    <template #date>
      <CheckinDateBar />
    </template>

    <template #header-action>
      <button type="button" class="ck-header-action" @click="foodMode = foodMode === 'preset' ? 'custom' : 'preset'">
        {{ foodMode === 'preset' ? '自定义' : '选预设' }}
      </button>
    </template>

    <template v-if="foodMode === 'preset'">
      <div v-if="foodCategories.length" class="ck-chip-row">
        <button
          v-for="c in foodCategories"
          :key="c.category_id"
          type="button"
          class="ck-chip"
          :class="{ active: selectedCategoryId === c.category_id }"
          @click="selectCategory(c.category_id)"
        >{{ c.category_name }}</button>
      </div>

      <div v-loading="presetsLoading" class="ck-food-grid">
        <div v-for="f in foodPresets" :key="f.food_id" class="ck-food-card" @click="openFoodPreset(f)">
          <div class="ck-food-card__img">
            <img v-if="f.image_url" :src="f.image_url" :alt="f.food_name" @error="onImgError" />
            <span v-else>🍽</span>
            <span class="ck-food-card__add"><el-icon :size="16"><Plus /></el-icon></span>
          </div>
          <span class="ck-food-card__name">{{ f.food_name }}</span>
          <span class="ck-food-card__kcal">{{ Math.round(f.calories_per_gram * 100) }}千卡/100g</span>
        </div>
      </div>
      <el-empty v-if="!presetsLoading && !foodPresets.length" :description="emptyPresetHint" />
    </template>

    <template v-else>
      <div class="custom-panel">
        <div class="upload-box" @click="foodFileInput?.click()">
          <img v-if="customFood.image_url" :src="customFood.image_url" class="upload-preview" alt="" />
          <template v-else>
            <el-icon :size="28"><Camera /></el-icon>
            <span>上传图片</span>
          </template>
          <div v-if="customFood.uploading" class="upload-mask">上传中…</div>
        </div>
        <input ref="foodFileInput" type="file" accept="image/*" hidden @change="onFoodFileChange" />
        <div v-if="customFood.image_object_key" class="ai-action-row">
          <el-button
            type="primary"
            plain
            round
            :loading="recognizing"
            :disabled="customFood.uploading"
            @click="runFoodRecognition"
          >AI 识别</el-button>
        </div>
        <div v-if="customFood.ai_summary || customFood.ai_tip" class="ai-hint-box">
          <p v-if="customFood.ai_summary">{{ customFood.ai_summary }}</p>
          <p v-if="customFood.ai_tip" class="ai-tip">{{ customFood.ai_tip }}</p>
        </div>
        <el-form label-position="top" class="compact-form">
          <el-form-item label="食物分类" required>
            <el-select v-model="customFood.category_id" placeholder="选择分类" style="width:100%">
              <el-option
                v-for="c in selectableFoodCategories"
                :key="c.category_id"
                :label="c.category_name"
                :value="c.category_id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="食物名称" required>
            <el-input v-model="customFood.name" placeholder="如：全麦面包" />
          </el-form-item>
          <el-form-item label="餐次" required>
            <el-select v-model="customFood.meal_period" style="width:100%">
              <el-option v-for="m in MEAL_PERIODS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="具体时间" required>
            <el-time-picker
              v-model="customFood.record_time"
              format="HH:mm"
              value-format="HH:mm"
              placeholder="选择时间"
              style="width:100%"
            />
          </el-form-item>
          <el-form-item label="是否液体"><el-switch v-model="customFood.is_liquid" /></el-form-item>
          <el-form-item label="每克卡路里 (kcal/g)" required>
            <el-input-number v-model="customFood.calories_per_gram" :min="0.01" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item v-if="customFood.is_liquid" label="ml→g 换算系数">
            <el-input-number v-model="customFood.ml_to_g_ratio" :min="0.01" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="食用量" required>
            <div class="amount-row">
              <el-input-number v-model="customFood.input_amount" :min="0.01" :precision="1" style="flex:1" />
              <el-select v-if="customFood.is_liquid" v-model="customFood.input_unit" style="width:88px">
                <el-option label="g" :value="1" />
                <el-option label="ml" :value="2" />
              </el-select>
              <span v-else class="unit-tag">g</span>
            </div>
          </el-form-item>
          <div class="preview-kcal">总热量：{{ calcFoodCalories(customFood) }} kcal</div>
          <el-button type="primary" round class="submit-btn" :loading="submitting" @click="submitCustomFood">确认打卡</el-button>
        </el-form>
      </div>
    </template>

    <template v-if="foodMode === 'preset'" #bottom>
      <div class="ck-summary-card">
        <div class="ck-summary-row">
          <div class="ck-summary-block">
            <p>当日累计摄入</p>
            <strong>{{ foodDailyTotalCalories }}</strong>
            <em> 千卡</em>
          </div>
          <div class="ck-summary-block ck-summary-target">
            <p>每日目标</p>
            <strong>{{ CALORIE_TARGET }}</strong>
            <em> 千卡</em>
          </div>
          <div class="ck-ring-wrap">
            <svg viewBox="0 0 72 72" class="ck-ring-svg">
              <circle cx="36" cy="36" r="30" fill="none" stroke="#f5f5f4" stroke-width="5" />
              <circle
                cx="36" cy="36" r="30" fill="none" stroke="#10b981" stroke-width="5"
                stroke-linecap="round"
                :stroke-dasharray="ringCircumference"
                :stroke-dashoffset="ringOffset"
                transform="rotate(-90 36 36)"
              />
            </svg>
            <span>{{ foodCaloriePercent }}%</span>
          </div>
        </div>
        <div class="ck-progress-bar">
          <div class="ck-progress-bar__fill" :style="{ width: `${foodCaloriePercent}%` }" />
        </div>
      </div>

      <div class="ck-records-sheet">
        <div class="ck-records-sheet__head">
          <h2>今日记录</h2>
          <span>{{ foodRecords.length }} 条记录</span>
        </div>
        <div class="ck-records-list">
          <div v-for="r in foodRecords" :key="r.checkin_id" class="ck-record-row">
            <div class="ck-record-thumb">
              <img v-if="r.image_url" :src="r.image_url" alt="" @error="onImgError" />
              <span v-else>🍽</span>
            </div>
            <div class="ck-record-body">
              <div class="ck-record-title-row">
                <span class="ck-record-title">{{ r.food_name }}</span>
                <span v-if="r.category_name" class="ck-meal-tag">{{ r.category_name }}</span>
              </div>
              <div class="ck-record-meta">
                <template v-if="formatRecordTime(r.record_time)">{{ formatRecordTime(r.record_time) }} · </template>
                {{ formatFoodAmount(r) }} · {{ r.total_calories }}千卡
              </div>
            </div>
            <div class="ck-record-status">
              <span class="ck-status-dot">✓</span>
              已完成
            </div>
          </div>
          <p v-if="!foodRecords.length" class="ck-empty-tip">暂无记录，点击上方食物添加</p>
        </div>
      </div>
    </template>
  </CheckinTypeLayout>

  <el-dialog v-model="foodDialogVisible" :title="foodDialogFood?.food_name" width="92%" style="max-width:420px" destroy-on-close>
    <div v-if="foodDialogFood" class="dialog-preset">
      <p class="dialog-meta">{{ foodDialogFood.calories_per_gram }} kcal/g</p>
      <el-form label-position="top">
        <el-form-item label="餐次" required>
          <el-select v-model="foodDialogMealPeriod" style="width:100%">
            <el-option v-for="m in MEAL_PERIODS" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="具体时间" required>
          <el-time-picker
            v-model="foodDialogTime"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="选择时间"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="食用量" required>
          <div class="amount-row">
            <el-input-number v-model="foodDialogAmount" :min="0.01" :precision="1" style="flex:1" />
            <el-select v-if="foodDialogFood.is_liquid" v-model="foodDialogUnit" style="width:88px">
              <el-option label="g" :value="1" />
              <el-option label="ml" :value="2" />
            </el-select>
            <span v-else class="unit-tag">g</span>
          </div>
        </el-form-item>
        <div class="preview-kcal">总热量：{{ calcPresetFoodCalories(foodDialogFood, foodDialogAmount, foodDialogUnit) }} kcal</div>
      </el-form>
    </div>
    <template #footer>
      <el-button round @click="foodDialogVisible = false">取消</el-button>
      <el-button type="primary" round :loading="submitting" @click="submitFoodPreset">确认打卡</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Camera } from '@element-plus/icons-vue'
import CheckinTypeLayout from './components/CheckinTypeLayout.vue'
import CheckinDateBar from './components/CheckinDateBar.vue'
import { MEAL_PERIODS, CALORIE_TARGET, CUSTOM_FOOD_CATEGORY_ID } from './checkin/constants'
import {
  calcFoodCalories,
  calcPresetFoodCalories,
  formatFoodAmount,
  formatRecordTime,
  inferMealPeriod,
  currentRecordTime,
  buildRecordDateTime,
  onImgError,
} from './checkin/utils'
import { useCheckinDate } from './composables/useCheckinDate'
import {
  getFoodCategories,
  getFoodPresets,
  createFoodCheckin,
  getFoodRecords,
  uploadCheckinImage,
  recognizeFoodImage,
} from '@/api/checkin'

const { checkinDate } = useCheckinDate()

const foodMode = ref('preset')
const presetsLoading = ref(false)
const submitting = ref(false)
const recognizing = ref(false)

const foodCategories = ref([])
const selectedCategoryId = ref('')
const foodPresets = ref([])
const foodRecords = ref([])

const foodDialogVisible = ref(false)
const foodDialogFood = ref(null)
const foodDialogAmount = ref(100)
const foodDialogUnit = ref(1)
const foodDialogMealPeriod = ref(1)
const foodDialogTime = ref(currentRecordTime())

const customFood = ref({
  name: '', category_id: '', calories_per_gram: 1, is_liquid: false, ml_to_g_ratio: 1,
  input_unit: 1, input_amount: 100, image_object_key: '', image_url: '', uploading: false,
  meal_period: inferMealPeriod(), record_time: currentRecordTime(),
  ai_summary: '', ai_tip: '', matched_food_id: '', ai_source_type: 2,
})

const foodFileInput = ref(null)
const ringCircumference = 2 * Math.PI * 30

const foodDailyTotalCalories = computed(() =>
  foodRecords.value.reduce((sum, r) => sum + (Number(r.total_calories) || 0), 0),
)

const foodCaloriePercent = computed(() =>
  Math.min(100, Math.round((foodDailyTotalCalories.value / CALORIE_TARGET) * 100)),
)

const ringOffset = computed(() =>
  ringCircumference * (1 - foodCaloriePercent.value / 100),
)

const isCustomCategory = computed(() => selectedCategoryId.value === CUSTOM_FOOD_CATEGORY_ID)

const emptyPresetHint = computed(() =>
  isCustomCategory.value ? '暂无自定义食物，可通过右上角「自定义」录入' : '暂无预设食物',
)

const selectableFoodCategories = computed(() =>
  foodCategories.value.filter((c) => c.category_id !== CUSTOM_FOOD_CATEGORY_ID),
)

onMounted(async () => {
  await loadCategories()
  await Promise.all([loadPresets(), loadRecords()])
})

watch(checkinDate, loadRecords)

async function loadCategories() {
  if (!foodCategories.value.length) {
    foodCategories.value = await getFoodCategories()
    selectedCategoryId.value = foodCategories.value[0]?.category_id || ''
  }
}

async function loadPresets() {
  presetsLoading.value = true
  try {
    if (selectedCategoryId.value) {
      foodPresets.value = await getFoodPresets(selectedCategoryId.value)
    }
  } finally {
    presetsLoading.value = false
  }
}

async function loadRecords() {
  foodRecords.value = await getFoodRecords(checkinDate.value)
}

async function selectCategory(id) {
  selectedCategoryId.value = id
  await loadPresets()
}

function resetDialogTimeFields() {
  foodDialogMealPeriod.value = inferMealPeriod()
  foodDialogTime.value = currentRecordTime()
}

function openFoodPreset(f) {
  foodDialogFood.value = f
  foodDialogAmount.value = f.is_liquid ? 200 : 100
  foodDialogUnit.value = f.is_liquid ? 2 : 1
  resetDialogTimeFields()
  foodDialogVisible.value = true
}

async function handleUpload(file, target) {
  if (!file) return
  target.uploading = true
  try {
    const res = await uploadCheckinImage('food', file)
    target.image_object_key = res.object_key
    target.image_url = res.image_url
    ElMessage.success('图片上传成功')
    await runFoodRecognition(target)
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    target.uploading = false
  }
}

function applyRecognitionResult(target, raw) {
  const data = normalizeRecognitionPayload(raw)
  if (data.has_error) {
    ElMessage.warning(data.error_message || '未能识别食物，请手动填写')
    target.ai_summary = ''
    target.ai_tip = ''
    return
  }
  if (data.food_name) target.name = data.food_name
  if (data.category_id) target.category_id = data.category_id
  if (data.calories_per_gram != null && data.calories_per_gram !== '') {
    target.calories_per_gram = Number(data.calories_per_gram)
  }
  if (data.is_liquid != null) target.is_liquid = toBool(data.is_liquid)
  if (data.ml_to_g_ratio != null && data.ml_to_g_ratio !== '') {
    target.ml_to_g_ratio = Number(data.ml_to_g_ratio)
  }
  if (data.suggested_input_amount != null && data.suggested_input_amount !== '') {
    target.input_amount = Number(data.suggested_input_amount)
  }
  if (data.suggested_input_unit != null && data.suggested_input_unit !== '') {
    target.input_unit = Number(data.suggested_input_unit)
  }
  target.ai_summary = data.recognition_summary || ''
  target.ai_tip = data.nutrition_tip || ''
  target.matched_food_id = data.matched_food_id || ''
  target.ai_source_type = data.source_type || 2
  ElMessage.success('AI 识别完成，请确认信息后打卡')
  if (target.matched_food_id && target.ai_source_type === 1) {
    ElMessageBox.confirm('识别结果匹配系统预设食物，是否切换为预设模式快速打卡？', '匹配预设', {
      confirmButtonText: '切换预设',
      cancelButtonText: '继续自定义',
      type: 'info',
    }).then(() => {
      foodMode.value = 'preset'
      const preset = foodPresets.value.find((f) => f.food_id === target.matched_food_id)
      if (preset) {
        openFoodPreset(preset)
      } else {
        selectedCategoryId.value = data.category_id || selectedCategoryId.value
        loadPresets().then(() => {
          const loaded = foodPresets.value.find((f) => f.food_id === target.matched_food_id)
          if (loaded) openFoodPreset(loaded)
        })
      }
    }).catch(() => {})
  }
}

function normalizeRecognitionPayload(raw) {
  if (!raw || typeof raw !== 'object') return {}
  if (Array.isArray(raw)) return normalizeRecognitionPayload(raw[0])
  const data = { ...raw }
  if (data.foodName && !data.food_name) data.food_name = data.foodName
  if (data.categoryId && !data.category_id) data.category_id = data.categoryId
  if (data.caloriesPerGram != null && data.calories_per_gram == null) data.calories_per_gram = data.caloriesPerGram
  if (data.isLiquid != null && data.is_liquid == null) data.is_liquid = data.isLiquid
  if (data.mlToGRatio != null && data.ml_to_g_ratio == null) data.ml_to_g_ratio = data.mlToGRatio
  if (data.suggestedInputAmount != null && data.suggested_input_amount == null) {
    data.suggested_input_amount = data.suggestedInputAmount
  }
  if (data.suggestedInputUnit != null && data.suggested_input_unit == null) {
    data.suggested_input_unit = data.suggestedInputUnit
  }
  if (data.matchedFoodId && !data.matched_food_id) data.matched_food_id = data.matchedFoodId
  if (data.sourceType != null && data.source_type == null) data.source_type = data.sourceType
  if (data.nutritionTip && !data.nutrition_tip) data.nutrition_tip = data.nutritionTip
  if (data.recognitionSummary && !data.recognition_summary) data.recognition_summary = data.recognitionSummary
  if (data.hasError != null && data.has_error == null) data.has_error = data.hasError
  if (data.errorMessage && !data.error_message) data.error_message = data.errorMessage
  return data
}

function toBool(value) {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  return value === 'true' || value === '1'
}

async function runFoodRecognition(target = customFood.value) {
  if (!target.image_object_key) return ElMessage.warning('请先上传图片')
  recognizing.value = true
  try {
    const data = await recognizeFoodImage({
      image_object_key: target.image_object_key,
      meal_period: target.meal_period,
    })
    applyRecognitionResult(target, data)
  } catch (e) {
    ElMessage.error(e.message || 'AI 识别失败')
  } finally {
    recognizing.value = false
  }
}

function onFoodFileChange(e) {
  handleUpload(e.target.files?.[0], customFood.value)
  e.target.value = ''
}

function buildSubmitRecordTime(timeStr) {
  return buildRecordDateTime(checkinDate.value, timeStr)
}

async function submitFoodPreset() {
  const f = foodDialogFood.value
  if (!f || !foodDialogAmount.value) return ElMessage.warning('请填写食用量')
  if (!foodDialogTime.value) return ElMessage.warning('请选择具体时间')
  submitting.value = true
  try {
    if (f.is_user_custom) {
      await createFoodCheckin({
        checkin_date: checkinDate.value,
        meal_period: foodDialogMealPeriod.value,
        record_time: buildSubmitRecordTime(foodDialogTime.value),
        source_type: 2,
        category_id: f.original_category_id,
        food_name: f.food_name,
        calories_per_gram: f.calories_per_gram,
        is_liquid: f.is_liquid,
        input_unit: f.is_liquid ? foodDialogUnit.value : 1,
        input_amount: foodDialogAmount.value,
        ml_to_g_ratio: f.is_liquid ? f.ml_to_g_ratio : 1,
        image_object_key: f.image_object_key,
      })
    } else {
      await createFoodCheckin({
        checkin_date: checkinDate.value,
        meal_period: foodDialogMealPeriod.value,
        record_time: buildSubmitRecordTime(foodDialogTime.value),
        source_type: 1,
        food_id: f.food_id,
        input_unit: foodDialogUnit.value,
        input_amount: foodDialogAmount.value,
        ml_to_g_ratio: f.ml_to_g_ratio,
        image_object_key: f.image_object_key,
      })
    }
    ElMessage.success('打卡成功')
    foodDialogVisible.value = false
    await Promise.all([loadPresets(), loadRecords()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

function resetCustomFood() {
  customFood.value = {
    name: '',
    category_id: foodCategories.value[0]?.category_id || '',
    calories_per_gram: 1,
    is_liquid: false,
    ml_to_g_ratio: 1,
    input_unit: 1,
    input_amount: 100,
    image_object_key: '',
    image_url: '',
    uploading: false,
    meal_period: inferMealPeriod(),
    record_time: currentRecordTime(),
    ai_summary: '',
    ai_tip: '',
    matched_food_id: '',
    ai_source_type: 2,
  }
}

async function submitCustomFood() {
  const c = customFood.value
  if (!c.category_id) return ElMessage.warning('请选择食物分类')
  if (!c.name?.trim()) return ElMessage.warning('请填写食物名称')
  if (!c.image_object_key) return ElMessage.warning('请先上传图片')
  if (!c.input_amount) return ElMessage.warning('请填写食用量')
  if (!c.record_time) return ElMessage.warning('请选择具体时间')
  submitting.value = true
  try {
    await createFoodCheckin({
      checkin_date: checkinDate.value,
      meal_period: c.meal_period,
      record_time: buildSubmitRecordTime(c.record_time),
      source_type: 2,
      category_id: c.category_id,
      food_name: c.name.trim(),
      calories_per_gram: c.calories_per_gram,
      is_liquid: c.is_liquid,
      input_unit: c.is_liquid ? c.input_unit : 1,
      input_amount: c.input_amount,
      ml_to_g_ratio: c.is_liquid ? c.ml_to_g_ratio : 1,
      image_object_key: c.image_object_key,
    })
    ElMessage.success('打卡成功')
    resetCustomFood()
    foodMode.value = 'preset'
    selectedCategoryId.value = CUSTOM_FOOD_CATEGORY_ID
    await Promise.all([loadPresets(), loadRecords()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
@import './checkin/checkin-theme.css';

.custom-panel {
  background: var(--ck-card);
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--ck-shadow);
  margin-top: 8px;
}

.upload-box {
  width: 120px;
  height: 120px;
  border: 2px dashed var(--ck-emerald-ring);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--ck-text-soft);
  cursor: pointer;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}

.upload-preview { width: 100%; height: 100%; object-fit: cover; }
.upload-mask {
  position: absolute;
  inset: 0;
  background: rgba(255,255,255,0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}

.amount-row { display: flex; gap: 8px; align-items: center; width: 100%; }
.unit-tag { font-size: 14px; color: var(--ck-text-muted); padding: 0 8px; }
.preview-kcal {
  text-align: center;
  font-weight: 700;
  color: var(--ck-emerald);
  margin: 8px 0 16px;
}

.submit-btn { width: 100%; }

.ai-action-row {
  margin: -8px 0 12px;
}

.ai-hint-box {
  background: #ecfdf5;
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--ck-text-muted);
  line-height: 1.5;
}

.ai-hint-box .ai-tip {
  margin-top: 6px;
  color: var(--ck-emerald);
  font-weight: 600;
}

.compact-form :deep(.el-button--primary) {
  --el-button-bg-color: var(--ck-emerald);
  --el-button-border-color: var(--ck-emerald);
}

.ck-ring-svg { width: 72px; height: 72px; }

.dialog-meta { text-align: center; color: var(--ck-text-muted); margin-bottom: 12px; }
.dialog-preset { text-align: left; }
</style>
