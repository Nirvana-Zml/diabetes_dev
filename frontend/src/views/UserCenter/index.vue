<template>
  <SiteLayout title="个人中心">

    <div class="page-container" v-loading="pageLoading">
      <!-- AI 异常指标预警 -->
      <el-alert
        v-if="healthAlert?.has_alert"
        :title="healthAlert.message"
        :type="healthAlert.level === 'error' ? 'error' : 'warning'"
        show-icon
        :closable="false"
        class="alert-banner"
      />

      <!-- 个人信息卡片 -->
      <div class="profile-hero">
        <div class="profile-main">
          <el-avatar :size="76" :src="profile?.avatar_url" class="avatar" />
          <div class="profile-text">
            <h2>{{ profile?.nickname || '用户' }}</h2>
            <p class="username">@{{ profile?.username }}</p>
            <div class="badges">
              <span class="badge points"><el-icon><Coin /></el-icon> {{ profile?.points ?? 0 }} 积分</span>
              <span class="badge streak"><el-icon><Calendar /></el-icon> 连续 {{ profile?.streak_days ?? 0 }} 天</span>
            </div>
          </div>
          <el-button circle :icon="Edit" @click="showProfileEdit = true" />
        </div>
        <div class="profile-extra">
          <span>{{ GENDER_LABELS[profile?.gender] || '—' }}</span>
          <span v-if="profile?.birth_date">· {{ profile.birth_date }}</span>
          <span v-if="profile?.phone">· {{ profile.phone }}</span>
        </div>
      </div>

      <!-- 快捷数据概览 -->
      <el-row :gutter="10" class="stat-row">
        <el-col :span="8" v-for="stat in quickStats" :key="stat.label">
          <div class="stat-card" @click="stat.action?.()">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- AI 健康趋势总结 -->
      <div class="section-card trend-card">
        <div class="section-header">
          <h3 class="section-title"><el-icon><TrendCharts /></el-icon> 月度健康趋势</h3>
          <el-tag size="small" type="info">AI 总结</el-tag>
        </div>
        <p class="trend-text">{{ trendSummary || '暂无足够数据生成趋势分析' }}</p>
      </div>

      <!-- 健康档案 -->
      <div class="section-card">
        <div class="section-header">
          <h3 class="section-title">健康档案</h3>
          <el-button link type="primary" @click="showHealthEdit = true">编辑</el-button>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="身高">{{ health.height }} cm</el-descriptions-item>
          <el-descriptions-item label="体重">{{ health.weight }} kg</el-descriptions-item>
          <el-descriptions-item label="BMI">
            <span :class="bmiClass">{{ health.bmi }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="空腹血糖">
            <span :class="glucoseClass">{{ health.fasting_glucose }} mmol/L</span>
          </el-descriptions-item>
          <el-descriptions-item label="餐后2h血糖">{{ health.postprandial_glucose ?? '—' }} mmol/L</el-descriptions-item>
          <el-descriptions-item label="糖化血红蛋白">{{ health.hba1c ?? '—' }} %</el-descriptions-item>
          <el-descriptions-item label="血压">{{ health.systolic_bp }}/{{ health.diastolic_bp }} mmHg</el-descriptions-item>
          <el-descriptions-item label="糖尿病类型">{{ DIABETES_TYPE_LABELS[health.diabetes_type] || '—' }}</el-descriptions-item>
          <el-descriptions-item label="运动频率">{{ EXERCISE_LABELS[health.exercise_freq] || '—' }}</el-descriptions-item>
          <el-descriptions-item label="饮食偏好">{{ DIET_LABELS[health.diet_type] || '—' }}</el-descriptions-item>
          <el-descriptions-item label="吸烟">{{ SMOKING_LABELS[health.smoking] || '—' }}</el-descriptions-item>
          <el-descriptions-item label="家族史">{{ health.family_history ? '有' : '无' }}</el-descriptions-item>
          <el-descriptions-item label="既往病史" :span="2">{{ formatMedicalHistory(health) }}</el-descriptions-item>
          <el-descriptions-item label="当前用药" :span="2">{{ formatMedication(health) }}</el-descriptions-item>
        </el-descriptions>
        <p v-if="health.recorded_at" class="record-time">最近更新：{{ formatTime(health.recorded_at) }}</p>
      </div>

      <!-- 功能入口：我的服务 -->
      <div class="section-card">
        <h3 class="section-title menu-group-title">我的服务</h3>
        <div
          v-for="item in serviceMenus"
          :key="item.key"
          class="menu-item"
          @click="handleMenu(item)"
        >
          <el-icon :size="20" color="#0d9488"><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ item.label }}</span>
          <span v-if="item.desc" class="menu-desc">{{ item.desc }}</span>
          <el-icon class="arrow"><ArrowRight /></el-icon>
        </div>
      </div>

      <!-- 数据与设置 -->
      <div class="section-card">
        <h3 class="section-title menu-group-title">数据与设置</h3>
        <div
          v-for="item in settingMenus"
          :key="item.key"
          class="menu-item"
          @click="handleMenu(item)"
        >
          <el-icon :size="20" color="#606266"><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ item.label }}</span>
          <el-icon class="arrow"><ArrowRight /></el-icon>
        </div>
      </div>

      <!-- 隐私与通知 -->
      <div class="section-card">
        <h3 class="section-title">隐私与通知</h3>
        <div class="switch-row">
          <div>
            <div class="switch-label">健康数据可见性</div>
            <div class="switch-desc">医生问诊时可查看您的健康档案</div>
          </div>
          <el-switch v-model="privacy.data_visible" @change="savePrivacy" />
        </div>
        <div class="switch-row">
          <div>
            <div class="switch-label">打卡提醒</div>
            <div class="switch-desc">未打卡时段推送提醒</div>
          </div>
          <el-switch v-model="privacy.checkin_notify" @change="savePrivacy" />
        </div>
        <div class="switch-row">
          <div>
            <div class="switch-label">问诊消息通知</div>
            <div class="switch-desc">医生回复时通知您</div>
          </div>
          <el-switch v-model="privacy.consult_notify" @change="savePrivacy" />
        </div>
        <div class="switch-row">
          <div>
            <div class="switch-label">健康资讯推送</div>
            <div class="switch-desc">接收个性化健康资讯</div>
          </div>
          <el-switch v-model="privacy.marketing_notify" @change="savePrivacy" />
        </div>
      </div>

      <el-button type="danger" plain class="logout-btn" @click="handleLogout">退出登录</el-button>
    </div>

    <ProfileEditDialog v-model="showProfileEdit" :profile="profile" @saved="onProfileSaved" />
    <HealthRecordDialog v-model="showHealthEdit" :record="health" @saved="onHealthSaved" />
    <ExportDialog v-model="showExport" />
    <ConsultationDrawer v-model="showConsultations" />
    <SecurityDialog v-model="showSecurity" :profile="profile" />
  </SiteLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Edit,
  Coin,
  Calendar,
  TrendCharts,
  Document,
  ChatLineRound,
  DataAnalysis,
  Download,
  Lock,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import ProfileEditDialog from './components/ProfileEditDialog.vue'
import HealthRecordDialog from './components/HealthRecordDialog.vue'
import ExportDialog from './components/ExportDialog.vue'
import ConsultationDrawer from './components/ConsultationDrawer.vue'
import SecurityDialog from './components/SecurityDialog.vue'
import {
  getUserProfile,
  getHealthRecord,
  getHealthAlert,
  getHealthTrendSummary,
  getUserConsultations,
  updatePrivacySettings,
} from '@/api/user'
import { getCheckinStats } from '@/api/checkin'
import { useUserStore } from '@/stores/user'
import {
  GENDER_LABELS,
  DIABETES_TYPE_LABELS,
  SMOKING_LABELS,
  EXERCISE_LABELS,
  DIET_LABELS,
} from './constants'

const router = useRouter()
const userStore = useUserStore()

const pageLoading = ref(false)
const profile = ref(null)
const health = ref({})
const healthAlert = ref(null)
const trendSummary = ref('')
const consultCount = ref(0)
const checkinStats = ref({})

const showProfileEdit = ref(false)
const showHealthEdit = ref(false)
const showExport = ref(false)
const showConsultations = ref(false)
const showSecurity = ref(false)

const privacy = reactive({
  data_visible: true,
  checkin_notify: true,
  consult_notify: true,
  marketing_notify: false,
})

const quickStats = computed(() => [
  {
    label: '打卡完成率',
    value: checkinStats.value.completion_rate != null
      ? Math.round(checkinStats.value.completion_rate * 100) + '%'
      : '—',
    action: () => router.push('/checkin-analysis'),
  },
  {
    label: '就诊次数',
    value: consultCount.value,
    action: () => { showConsultations.value = true },
  },
  {
    label: '总积分',
    value: profile.value?.points ?? 0,
    action: () => router.push('/checkin-records'),
  },
])

const serviceMenus = [
  { key: 'plan', label: '健康方案', icon: Document, path: '/living-plans', desc: '饮食/运动/作息方案' },
  { key: 'consult', label: '就诊记录', icon: ChatLineRound, action: 'consultations' }
]

const settingMenus = [
  { key: 'export', label: '数据导出', icon: Download, action: 'export' },
  { key: 'security', label: '账户安全', icon: Lock, action: 'security' },
]

const bmiClass = computed(() => {
  const bmi = health.value?.bmi
  if (!bmi) return ''
  if (bmi >= 24) return 'value-warn'
  return 'value-normal'
})

const glucoseClass = computed(() => {
  const g = health.value?.fasting_glucose
  if (!g) return ''
  if (g >= 6.1) return 'value-warn'
  return 'value-normal'
})

onMounted(loadPage)

async function loadPage() {
  pageLoading.value = true
  try {
    const [p, h, alert, trend, consults, stats] = await Promise.all([
      getUserProfile(),
      getHealthRecord(),
      getHealthAlert(),
      getHealthTrendSummary(),
      getUserConsultations(),
      getCheckinStats(),
    ])
    profile.value = p
    health.value = h
    healthAlert.value = alert
    trendSummary.value = trend.summary
    consultCount.value = consults.total
    checkinStats.value = stats
    const ps = p.privacy_settings || {}
    privacy.data_visible = ps.data_visible ?? true
    privacy.checkin_notify = ps.checkin_notify ?? true
    privacy.consult_notify = ps.consult_notify ?? true
    privacy.marketing_notify = ps.marketing_notify ?? false
    userStore.profile = p
  } finally {
    pageLoading.value = false
  }
}

function formatTime(t) {
  return dayjs(t).format('YYYY-MM-DD HH:mm')
}

function formatMedicalHistory(h) {
  if (!h) return '—'
  if (h.medical_history) return h.medical_history
  const list = h.medical_histories || []
  if (!list.length) return '—'
  return list.map((item) => item.disease_name || item.diseaseName).filter(Boolean).join('；')
}

function formatMedication(h) {
  if (!h) return '—'
  if (h.medication) return h.medication
  const list = h.medications || []
  if (!list.length) return '—'
  return list.map((item) => {
    const name = item.drug_name || item.drugName
    if (!name) return ''
    const dosage = item.dosage || ''
    const freq = item.frequency_desc || item.frequencyDesc || ''
    const detail = [dosage, freq].filter(Boolean).join(' ')
    return detail ? `${name}（${detail}）` : name
  }).filter(Boolean).join('；')
}

function onProfileSaved(data) {
  profile.value = data
  userStore.profile = data
}

function onHealthSaved(data) {
  health.value = data
}

function handleMenu(item) {
  if (item.action === 'consultations') showConsultations.value = true
  else if (item.action === 'export') showExport.value = true
  else if (item.action === 'security') showSecurity.value = true
  else if (item.path) router.push(item.path)
}

async function savePrivacy() {
  await updatePrivacySettings({ ...privacy })
  ElMessage.success('设置已保存')
}

async function handleLogout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.alert-banner { margin-bottom: 12px; border-radius: 10px; }

.profile-hero {
  background: linear-gradient(135deg, #0d9488 0%, #14b8a6 100%);
  border-radius: 16px;
  padding: 20px;
  color: #fff;
  margin-bottom: 12px;
  box-shadow: 0 8px 24px rgba(13, 148, 136, 0.25);
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  border: 3px solid rgba(255, 255, 255, 0.6);
  flex-shrink: 0;
}

.profile-text {
  flex: 1;
  min-width: 0;
}

.profile-text h2 {
  margin: 0 0 2px;
  font-size: 20px;
}

.username {
  margin: 0 0 8px;
  font-size: 13px;
  opacity: 0.85;
}

.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  padding: 4px 10px;
  font-size: 12px;
}

.profile-extra {
  margin-top: 12px;
  font-size: 13px;
  opacity: 0.9;
}

.stat-row { margin-bottom: 12px; }

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 8px;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s;
}

.stat-card:active { transform: scale(0.98); }

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #0d9488;
}

.stat-label {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.trend-card .section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}

.trend-text {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header .section-title { margin: 0; }

.menu-group-title {
  margin: 0 0 4px;
  font-size: 15px;
  color: #909399;
  font-weight: 500;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
}

.menu-item:last-child { border-bottom: none; }

.menu-label {
  font-size: 15px;
  color: #303133;
}

.menu-desc {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
  margin-right: 4px;
}

.menu-item .arrow {
  color: #c0c4cc;
  flex-shrink: 0;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}

.switch-row:last-child { border-bottom: none; }

.switch-label {
  font-size: 15px;
  color: #303133;
}

.switch-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.record-time {
  margin: 10px 0 0;
  font-size: 12px;
  color: #c0c4cc;
  text-align: right;
}

.value-warn { color: #e6a23c; font-weight: 600; }
.value-normal { color: #67c23a; }

.logout-btn {
  width: 100%;
  margin: 8px 0 24px;
}
</style>
