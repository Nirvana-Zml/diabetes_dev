<template>
  <CheckinTypeLayout title="用药打卡" accent="indigo">
    <template #header-action>
      <button type="button" class="ck-header-action" @click="medMode = medMode === 'preset' ? 'custom' : 'preset'">
        {{ medMode === 'preset' ? '自定义' : '选预设' }}
      </button>
    </template>

    <template #date>
      <CheckinDateBar />
    </template>

    <template v-if="medMode === 'preset'">
      <div class="ck-chip-row">
        <button
          v-for="tab in medPresetTabs"
          :key="tab.key"
          type="button"
          class="ck-chip"
          :class="{ active: medPresetTab === tab.key }"
          @click="medPresetTab = tab.key"
        >{{ tab.label }}</button>
      </div>

      <div v-loading="presetsLoading" class="ck-med-grid">
        <div v-for="d in filteredMedicationPresets" :key="d.drug_id" class="ck-med-card" @click="openMedPreset(d)">
          <div class="ck-med-card__thumb">
            <img v-if="d.image_url" :src="d.image_url" :alt="d.drug_name" @error="onImgError" />
            <span v-else>💊</span>
            <span class="ck-med-card__add"><el-icon :size="16"><Plus /></el-icon></span>
          </div>
          <span class="ck-med-card__name">{{ d.drug_name }}</span>
        </div>
      </div>
      <el-empty v-if="!presetsLoading && !filteredMedicationPresets.length" :description="medEmptyHint" />
    </template>

    <template v-else>
      <div class="custom-panel">
        <div class="upload-box" @click="medFileInput?.click()">
          <img v-if="customMed.image_url" :src="customMed.image_url" class="upload-preview" alt="" />
          <template v-else>
            <el-icon :size="28"><Camera /></el-icon>
            <span>上传药品图片</span>
          </template>
          <div v-if="customMed.uploading" class="upload-mask">上传中…</div>
        </div>
        <input ref="medFileInput" type="file" accept="image/*" hidden @change="onMedFileChange" />
        <el-form label-position="top" class="compact-form">
          <el-form-item label="药品名称" required><el-input v-model="customMed.name" /></el-form-item>
          <el-form-item label="剂量" required><el-input v-model="customMed.dosage" placeholder="如：0.5g" /></el-form-item>
          <el-form-item label="是否已服"><el-switch v-model="customMed.taken" /></el-form-item>
          <el-button type="primary" round class="submit-btn" :loading="submitting" @click="submitCustomMed">确认打卡</el-button>
        </el-form>
      </div>
    </template>

    <template #bottom>
      <div class="ck-records-sheet ck-records-sheet--solo">
        <div class="ck-records-sheet__head">
          <h2>今日记录</h2>
          <span>{{ medicationRecords.length }} 条记录</span>
        </div>
        <div class="ck-records-list ck-records-list--tall">
          <div v-for="r in medicationRecords" :key="r.checkin_id" class="ck-record-row">
            <div class="ck-record-thumb">
              <img v-if="r.image_url" :src="r.image_url" alt="" @error="onImgError" />
              <span v-else>💊</span>
            </div>
            <div class="ck-record-body">
              <div class="ck-record-title-row">
                <span class="ck-record-title">{{ r.drug_name }}</span>
                <el-tag :type="r.taken ? 'success' : 'info'" size="small">{{ r.taken ? '已服' : '未服' }}</el-tag>
              </div>
              <div class="ck-record-meta">{{ r.dosage }} · {{ formatTime(r.record_time) }}</div>
            </div>
            <div class="ck-record-status">
              <span class="ck-status-dot">✓</span>
              已完成
            </div>
          </div>
          <p v-if="!medicationRecords.length" class="ck-empty-tip">暂无记录，点击上方药品添加</p>
        </div>
      </div>
    </template>
  </CheckinTypeLayout>

  <el-dialog v-model="medDialogVisible" :title="medDialogDrug?.drug_name" width="92%" style="max-width:420px" destroy-on-close>
    <div v-if="medDialogDrug" class="dialog-preset">
      <el-form label-position="top">
        <el-form-item label="剂量" required><el-input v-model="medDialogDosage" placeholder="如：0.5g" /></el-form-item>
        <el-form-item label="是否已服"><el-switch v-model="medDialogTaken" /></el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button round @click="medDialogVisible = false">取消</el-button>
      <el-button type="primary" round :loading="submitting" @click="submitMedPreset">确认打卡</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Camera } from '@element-plus/icons-vue'
import CheckinTypeLayout from './components/CheckinTypeLayout.vue'
import CheckinDateBar from './components/CheckinDateBar.vue'
import { useCheckinDate } from './composables/useCheckinDate'
import { formatTime, onImgError } from './checkin/utils'
import {
  getMedicationPresets,
  createMedicationCheckin,
  getMedicationRecords,
  uploadCheckinImage,
} from '@/api/checkin'

const { checkinDate } = useCheckinDate()

const medMode = ref('preset')
const medPresetTab = ref('system')
const presetsLoading = ref(false)
const submitting = ref(false)
const medicationPresets = ref([])
const medicationRecords = ref([])

const medPresetTabs = [
  { key: 'system', label: '系统药品' },
  { key: 'custom', label: '自定义' },
]

const filteredMedicationPresets = computed(() =>
  medicationPresets.value.filter((d) =>
    medPresetTab.value === 'custom' ? d.is_user_custom : !d.is_user_custom,
  ),
)

const medEmptyHint = computed(() =>
  medPresetTab.value === 'custom'
    ? '暂无自定义药品，可通过右上角「自定义」录入'
    : '暂无预设药品',
)

const medDialogVisible = ref(false)
const medDialogDrug = ref(null)
const medDialogDosage = ref('')
const medDialogTaken = ref(true)

const customMed = ref({
  name: '', dosage: '', taken: true, image_object_key: '', image_url: '', uploading: false,
})
const medFileInput = ref(null)

onMounted(async () => {
  await Promise.all([loadPresets(), loadRecords()])
})

watch(checkinDate, loadRecords)

async function loadPresets() {
  presetsLoading.value = true
  try {
    medicationPresets.value = await getMedicationPresets()
  } finally {
    presetsLoading.value = false
  }
}

async function loadRecords() {
  if (!checkinDate.value) return
  medicationRecords.value = await getMedicationRecords(checkinDate.value)
}

function openMedPreset(d) {
  medDialogDrug.value = d
  medDialogDosage.value = d.default_dosage || ''
  medDialogTaken.value = true
  medDialogVisible.value = true
}

async function handleUpload(file, target) {
  if (!file) return
  target.uploading = true
  try {
    const res = await uploadCheckinImage('medical', file)
    target.image_object_key = res.object_key
    target.image_url = res.image_url
    ElMessage.success('图片上传成功')
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    target.uploading = false
  }
}

function onMedFileChange(e) {
  handleUpload(e.target.files?.[0], customMed.value)
  e.target.value = ''
}

async function submitMedPreset() {
  const d = medDialogDrug.value
  if (!d || !medDialogDosage.value?.trim()) return ElMessage.warning('请填写剂量')
  submitting.value = true
  try {
    if (d.is_user_custom) {
      await createMedicationCheckin({
        checkin_date: checkinDate.value,
        source_type: 2,
        drug_name: d.drug_name,
        dosage: medDialogDosage.value.trim(),
        taken: medDialogTaken.value,
        image_object_key: d.image_object_key,
      })
    } else {
      await createMedicationCheckin({
        checkin_date: checkinDate.value,
        source_type: 1,
        drug_id: d.drug_id,
        dosage: medDialogDosage.value.trim(),
        taken: medDialogTaken.value,
        image_object_key: d.image_object_key,
      })
    }
    ElMessage.success('打卡成功')
    medDialogVisible.value = false
    await Promise.all([loadPresets(), loadRecords()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}

async function submitCustomMed() {
  const c = customMed.value
  if (!c.name?.trim() || !c.dosage?.trim()) return ElMessage.warning('请填写药品名称和剂量')
  if (!c.image_object_key) return ElMessage.warning('请先上传图片')
  submitting.value = true
  try {
    await createMedicationCheckin({
      checkin_date: checkinDate.value,
      source_type: 2,
      drug_name: c.name.trim(),
      dosage: c.dosage.trim(),
      taken: c.taken,
      image_object_key: c.image_object_key,
    })
    ElMessage.success('打卡成功')
    customMed.value = { name: '', dosage: '', taken: true, image_object_key: '', image_url: '', uploading: false }
    medMode.value = 'preset'
    medPresetTab.value = 'custom'
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
  border: 2px dashed #c7d2fe;
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

.submit-btn { width: 100%; }

.compact-form :deep(.el-button--primary) {
  --el-button-bg-color: #6366f1;
  --el-button-border-color: #6366f1;
}

.ck-med-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding-top: 8px;
}

.ck-med-card {
  background: var(--ck-card);
  border-radius: 16px;
  padding: 12px;
  box-shadow: var(--ck-shadow);
  cursor: pointer;
}

.ck-med-card__thumb {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 12px;
  background: #eef2ff;
  overflow: hidden;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.ck-med-card__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ck-med-card__add {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #6366f1;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.35);
}

.ck-med-card__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--ck-text);
}

.ck-records-sheet--solo {
  margin: 0 16px 12px;
  border-radius: 16px;
  box-shadow: var(--ck-shadow-lg);
}

.ck-records-list--tall {
  max-height: 240px;
}
</style>
