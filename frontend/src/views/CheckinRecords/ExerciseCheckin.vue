<template>
  <CheckinTypeLayout title="运动打卡" accent="amber">
    <template #header-action>
      <button type="button" class="ck-header-action" @click="exMode = exMode === 'preset' ? 'custom' : 'preset'">
        {{ exMode === 'preset' ? '自定义' : '选预设' }}
      </button>
    </template>

    <template #date>
      <CheckinDateBar />
    </template>

    <template v-if="exMode === 'preset'">
      <div v-loading="presetsLoading" class="ck-ex-grid">
        <div v-for="e in exercisePresets" :key="e.exercise_id" class="ck-ex-card" @click="openExPreset(e)">
          <div class="ck-ex-card__icon">🏃</div>
          <div class="ck-ex-card__body">
            <span class="ck-ex-card__name">{{ e.exercise_name }}</span>
            <span class="ck-ex-card__kcal">{{ e.calories_per_minute }} kcal/min</span>
          </div>
          <span class="ck-ex-card__add"><el-icon :size="16"><Plus /></el-icon></span>
        </div>
      </div>
      <el-empty v-if="!presetsLoading && !exercisePresets.length" description="暂无预设运动" />
    </template>

    <template v-else>
      <div class="custom-panel">
        <el-form label-position="top" class="compact-form">
          <el-form-item label="运动项目名称" required><el-input v-model="customEx.name" /></el-form-item>
          <el-form-item label="每分钟消耗 (kcal/min)" required>
            <el-input-number v-model="customEx.calories_per_minute" :min="0.01" :step="0.1" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="运动分钟数" required>
            <el-input-number v-model="customEx.duration" :min="1" :max="300" style="width:100%" />
          </el-form-item>
          <div class="preview-kcal">总消耗：{{ calcExCalories(customEx.calories_per_minute, customEx.duration) }} kcal</div>
          <el-button type="primary" round class="submit-btn" :loading="submitting" @click="submitCustomEx">确认打卡</el-button>
        </el-form>
      </div>
    </template>

    <template #bottom>
      <div class="ck-summary-card ck-summary-card--ex">
        <div class="ck-summary-row">
          <div class="ck-summary-block">
            <p>今日消耗</p>
            <strong>{{ exerciseDailyCalories }}</strong>
            <em> 千卡</em>
          </div>
          <div class="ck-summary-block">
            <p>运动次数</p>
            <strong>{{ exerciseRecords.length }}</strong>
            <em> 次</em>
          </div>
        </div>
      </div>

      <div class="ck-records-sheet">
        <div class="ck-records-sheet__head">
          <h2>今日记录</h2>
          <span>{{ exerciseRecords.length }} 条记录</span>
        </div>
        <div class="ck-records-list">
          <div v-for="r in exerciseRecords" :key="r.checkin_id" class="ck-record-row">
            <div class="ck-record-thumb ck-record-thumb--ex">🏃</div>
            <div class="ck-record-body">
              <div class="ck-record-title-row">
                <span class="ck-record-title">{{ r.exercise_name }}</span>
              </div>
              <div class="ck-record-meta">{{ r.duration_minutes }} 分钟 · {{ formatTime(r.record_time) }}</div>
            </div>
            <div class="ck-record-right">
              <strong>{{ r.calories_burned }} kcal</strong>
            </div>
          </div>
          <p v-if="!exerciseRecords.length" class="ck-empty-tip">暂无记录，点击上方运动添加</p>
        </div>
      </div>
    </template>
  </CheckinTypeLayout>

  <el-dialog v-model="exDialogVisible" :title="exDialogItem?.exercise_name" width="92%" style="max-width:420px" destroy-on-close>
    <div v-if="exDialogItem" class="dialog-preset">
      <p class="dialog-meta">{{ exDialogItem.calories_per_minute }} kcal/min</p>
      <el-form label-position="top">
        <el-form-item label="运动分钟数" required>
          <el-input-number v-model="exDialogDuration" :min="1" :max="300" style="width:100%" />
        </el-form-item>
        <div class="preview-kcal">总消耗：{{ calcExCalories(exDialogItem.calories_per_minute, exDialogDuration) }} kcal</div>
      </el-form>
    </div>
    <template #footer>
      <el-button round @click="exDialogVisible = false">取消</el-button>
      <el-button type="primary" round :loading="submitting" @click="submitExPreset">确认打卡</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import CheckinTypeLayout from './components/CheckinTypeLayout.vue'
import CheckinDateBar from './components/CheckinDateBar.vue'
import { useCheckinDate } from './composables/useCheckinDate'
import { calcExCalories, formatTime } from './checkin/utils'
import {
  getExercisePresets,
  createExerciseCheckin,
  getExerciseRecords,
} from '@/api/checkin'

const { checkinDate } = useCheckinDate()

const exMode = ref('preset')
const presetsLoading = ref(false)
const submitting = ref(false)
const exercisePresets = ref([])
const exerciseRecords = ref([])

const exDialogVisible = ref(false)
const exDialogItem = ref(null)
const exDialogDuration = ref(30)

const customEx = ref({ name: '', calories_per_minute: 5, duration: 30 })

const exerciseDailyCalories = computed(() =>
  exerciseRecords.value.reduce((sum, r) => sum + (Number(r.calories_burned) || 0), 0),
)

onMounted(async () => {
  await Promise.all([loadPresets(), loadRecords()])
})

watch(checkinDate, loadRecords)

async function loadPresets() {
  presetsLoading.value = true
  try {
    exercisePresets.value = await getExercisePresets()
  } finally {
    presetsLoading.value = false
  }
}

async function loadRecords() {
  exerciseRecords.value = await getExerciseRecords(checkinDate.value)
}

function openExPreset(e) {
  exDialogItem.value = e
  exDialogDuration.value = 30
  exDialogVisible.value = true
}

async function submitExPreset() {
  const e = exDialogItem.value
  if (!e || !exDialogDuration.value) return ElMessage.warning('请填写运动分钟数')
  submitting.value = true
  try {
    await createExerciseCheckin({
      checkin_date: checkinDate.value,
      source_type: 1,
      exercise_id: e.exercise_id,
      duration_minutes: exDialogDuration.value,
    })
    ElMessage.success('打卡成功')
    exDialogVisible.value = false
    await loadRecords()
  } catch (err) {
    ElMessage.error(err.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitCustomEx() {
  const c = customEx.value
  if (!c.name?.trim() || !c.duration) return ElMessage.warning('请填写完整信息')
  submitting.value = true
  try {
    await createExerciseCheckin({
      checkin_date: checkinDate.value,
      source_type: 2,
      exercise_name: c.name.trim(),
      calories_per_minute: c.calories_per_minute,
      duration_minutes: c.duration,
    })
    ElMessage.success('打卡成功')
    customEx.value = { name: '', calories_per_minute: 5, duration: 30 }
    exMode.value = 'preset'
    await loadRecords()
  } catch (err) {
    ElMessage.error(err.message || '打卡失败')
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

.preview-kcal {
  text-align: center;
  font-weight: 700;
  color: #f59e0b;
  margin: 8px 0 16px;
}

.submit-btn { width: 100%; }

.compact-form :deep(.el-button--primary) {
  --el-button-bg-color: #f59e0b;
  --el-button-border-color: #f59e0b;
}

.ck-ex-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 8px;
}

.ck-ex-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--ck-card);
  border-radius: 16px;
  padding: 14px 16px;
  box-shadow: var(--ck-shadow);
  cursor: pointer;
}

.ck-ex-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #fffbeb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.ck-ex-card__body { flex: 1; min-width: 0; }

.ck-ex-card__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--ck-text);
}

.ck-ex-card__kcal {
  font-size: 12px;
  color: var(--ck-text-soft);
}

.ck-ex-card__add {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.35);
}

.ck-summary-card--ex .ck-summary-block strong { color: #d97706; }

.ck-record-thumb--ex { background: #fffbeb; font-size: 22px; }

.ck-record-right strong {
  font-size: 14px;
  font-weight: 700;
  color: #d97706;
}

.dialog-meta { text-align: center; color: var(--ck-text-muted); margin-bottom: 12px; }
</style>
