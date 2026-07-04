<template>
  <SiteLayout title="个人中心">

    <div class="page-container uc-page" v-loading="pageLoading">
      <!-- AI 异常指标预警 -->
      <div
        v-if="healthAlert?.has_alert"
        class="alert-banner"
        :class="healthAlert.level === 'error' ? 'alert-banner--error' : 'alert-banner--warning'"
      >
        <svg class="alert-banner__icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
        <div class="alert-banner__body">
          <p class="alert-banner__text">{{ healthAlert.message }}</p>
          <p v-if="healthAlert.suggestion" class="alert-banner__sub">{{ healthAlert.suggestion }}</p>
          <div class="alert-banner__actions">
            <el-button size="small" type="primary" @click="goGlucoseCheckin">记录血糖</el-button>
            <el-button size="small" @click="ackHealthAlert">知道了</el-button>
          </div>
        </div>
      </div>

      <div class="uc-layout">
        <!-- 左侧：个人信息与统计 -->
        <aside class="uc-sidebar">
          <div class="profile-hero">
            <button type="button" class="profile-edit-btn" aria-label="编辑资料" @click="showProfileEdit = true">
              <el-icon><Edit /></el-icon>
            </button>
            <div class="profile-hero__avatar-wrap">
              <el-avatar :size="avatarSize" :src="profile?.avatar_url" class="avatar" />
            </div>
            <div class="profile-text">
              <h2>{{ profile?.nickname || '用户' }}</h2>
              <p class="username">@{{ profile?.username }}</p>
              <div class="badges">
                <span class="badge"><el-icon><Coin /></el-icon> {{ profile?.points ?? 0 }} 积分</span>
                <span class="badge"><el-icon><Calendar /></el-icon> 连续 {{ profile?.streak_days ?? 0 }} 天</span>
              </div>
              <p class="profile-extra">
                <span>{{ GENDER_LABELS[profile?.gender] || '—' }}</span>
                <span v-if="profile?.birth_date"> · {{ profile.birth_date }}</span>
                <span v-if="profile?.phone"> · {{ profile.phone }}</span>
              </p>
            </div>
          </div>

          <div class="stat-stack">
            <div
              v-for="stat in quickStats"
              :key="stat.label"
              class="stat-card"
              @click="stat.action?.()"
            >
              <p class="stat-value">{{ stat.value }}</p>
              <p class="stat-label">{{ stat.label }}</p>
            </div>
          </div>

          <button type="button" class="logout-btn" @click="handleLogout">退出登录</button>
        </aside>

        <!-- 右侧：健康档案与服务 -->
        <div class="uc-main">
      <!-- AI 健康趋势总结 -->
      <div class="section-card trend-card" v-loading="trendLoading">
        <div class="section-header">
          <div class="section-title-wrap">
            <svg class="section-title-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <h3 class="section-title">月度健康趋势</h3>
          </div>
          <span class="ai-tag">AI 总结</span>
        </div>
        <p class="trend-text">{{ trendSummary || '暂无足够数据生成趋势分析' }}</p>
        <p v-if="trendLoading && !trendSummary" class="trend-hint">正在更新 AI 分析…</p>
      </div>

      <!-- 健康档案 -->
      <div class="section-card">
        <div class="section-header">
          <h3 class="section-title">健康档案</h3>
          <button type="button" class="text-link" @click="showHealthEdit = true">编辑</button>
        </div>
        <div class="health-grid">
          <div class="health-row">
            <span class="health-label">身高</span>
            <span class="health-value">{{ health.height }} cm</span>
          </div>
          <div class="health-row">
            <span class="health-label">体重</span>
            <span class="health-value">{{ health.weight }} kg</span>
          </div>
          <div class="health-row">
            <span class="health-label">BMI</span>
            <span class="health-value" :class="bmiClass">{{ health.bmi }}</span>
          </div>
          <div class="health-row">
            <span class="health-label">空腹血糖</span>
            <span class="health-value" :class="glucoseClass">{{ health.fasting_glucose }} mmol/L</span>
          </div>
          <div class="health-row">
            <span class="health-label">餐后2h血糖</span>
            <span class="health-value">{{ health.postprandial_glucose ?? '—' }} mmol/L</span>
          </div>
          <div class="health-row">
            <span class="health-label">糖化血红蛋白</span>
            <span class="health-value">{{ health.hba1c ?? '—' }} %</span>
          </div>
          <div class="health-row">
            <span class="health-label">血压</span>
            <span class="health-value">{{ health.systolic_bp }}/{{ health.diastolic_bp }} mmHg</span>
          </div>
          <div class="health-row">
            <span class="health-label">糖尿病类型</span>
            <span class="health-value" :class="{ 'health-value--muted': !health.diabetes_type }">
              {{ DIABETES_TYPE_LABELS[health.diabetes_type] || '—' }}
            </span>
          </div>
          <div class="health-row">
            <span class="health-label">运动频率</span>
            <span class="health-value" :class="{ 'health-value--muted': !health.exercise_freq }">
              {{ EXERCISE_LABELS[health.exercise_freq] || '—' }}
            </span>
          </div>
          <div class="health-row">
            <span class="health-label">饮食偏好</span>
            <span class="health-value">{{ DIET_LABELS[health.diet_type] || '—' }}</span>
          </div>
          <div class="health-row">
            <span class="health-label">吸烟</span>
            <span class="health-value" :class="{ 'health-value--muted': !health.smoking }">
              {{ SMOKING_LABELS[health.smoking] || '—' }}
            </span>
          </div>
          <div class="health-row">
            <span class="health-label">家族史</span>
            <span class="health-value">{{ health.family_history ? '有' : '无' }}</span>
          </div>
          <div class="health-row">
            <span class="health-label">既往病史</span>
            <span class="health-value">{{ formatMedicalHistory(health) }}</span>
          </div>
          <div class="health-row">
            <span class="health-label">当前用药</span>
            <span class="health-value">{{ formatMedication(health) }}</span>
          </div>
        </div>
        <p v-if="health.recorded_at" class="record-time">最近更新：{{ formatTime(health.recorded_at) }}</p>
      </div>

      <div class="menu-row">
        <!-- 功能入口：我的服务 -->
        <div class="section-card menu-row__item">
          <h3 class="section-title menu-group-title">我的服务</h3>
          <div class="menu-list">
            <div
              v-for="item in serviceMenus"
              :key="item.key"
              class="menu-item"
              @click="handleMenu(item)"
            >
              <div class="menu-item__left">
                <div class="menu-icon menu-icon--primary">
                  <el-icon :size="20"><component :is="item.icon" /></el-icon>
                </div>
                <span class="menu-label">{{ item.label }}</span>
              </div>
              <div class="menu-item__right">
                <span v-if="item.desc" class="menu-desc">{{ item.desc }}</span>
                <el-icon class="arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 数据与设置 -->
        <div class="section-card menu-row__item">
          <h3 class="section-title menu-group-title">数据与设置</h3>
          <div class="menu-list">
            <div
              v-for="item in settingMenus"
              :key="item.key"
              class="menu-item"
              @click="handleMenu(item)"
            >
              <div class="menu-item__left">
                <div class="menu-icon menu-icon--gray">
                  <el-icon :size="20"><component :is="item.icon" /></el-icon>
                </div>
                <span class="menu-label">{{ item.label }}</span>
              </div>
              <el-icon class="arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 隐私与通知 -->
      <div class="section-card">
        <h3 class="section-title privacy-title">隐私与通知</h3>
        <div class="switch-list">
          <div class="switch-row">
            <div>
              <div class="switch-label">健康数据可见性</div>
              <div class="switch-desc">医生问诊时可查看您的健康档案</div>
            </div>
            <el-switch v-model="privacy.data_visible" @change="savePrivacy" />
          </div>
          <div ref="checkinNotifyRef" class="switch-row switch-row--highlight">
            <div>
              <div class="switch-label">打卡提醒</div>
              <div class="switch-desc">在您设定的时段提醒您完成饮食、运动、用药、血糖打卡</div>
              <div class="switch-links">
                <button type="button" class="text-link" @click="router.push('/checkin-reminder-settings')">
                  管理提醒时段
                </button>
                <button
                  v-if="notificationSupported && browserNotifyLabel !== '已授权'"
                  type="button"
                  class="text-link"
                  @click="enableBrowserNotify"
                >
                  开启浏览器通知
                </button>
              </div>
            </div>
            <el-switch v-model="privacy.checkin_notify" @change="savePrivacy" />
          </div>
          <div class="switch-row">
            <div>
              <div class="switch-label">消息通知</div>
              <div class="switch-desc">风险评估、健康方案、医生回复、打卡分析完成或失败时通知您</div>
              <div class="switch-links">
                <button
                  v-if="notificationSupported && browserNotifyLabel !== '已授权'"
                  type="button"
                  class="text-link"
                  @click="enableBrowserNotify"
                >
                  开启浏览器通知
                </button>
              </div>
            </div>
            <el-switch v-model="privacy.message_notify" @change="savePrivacy" />
          </div>
        </div>
      </div>

      <!-- 移动端：平台与服务 -->
      <div v-if="isMobile" class="section-card">
        <div class="menu-list">
          <div class="menu-item" @click="showPlatformInfo = true">
            <div class="menu-item__left">
              <div class="menu-icon menu-icon--gray">
                <el-icon :size="20"><InfoFilled /></el-icon>
              </div>
              <span class="menu-label">平台与服务</span>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
        </div>
      </div>
    </div>

    <ProfileEditDialog v-model="showProfileEdit" :profile="profile" @saved="onProfileSaved" />
    <HealthRecordDialog v-model="showHealthEdit" :record="health" @saved="onHealthSaved" />
    <ExportDialog v-model="showExport" />
    <ConsultationDrawer v-model="showConsultations" />
    <SecurityDialog v-model="showSecurity" :profile="profile" @saved="onProfileSaved" />

    <el-dialog
      v-model="showPlatformInfo"
      title="平台与服务"
      width="92%"
      style="max-width: 420px"
      destroy-on-close
    >
      <div class="platform-info-dialog">
        <div class="platform-info-dialog__brand">
          <span class="platform-info-dialog__logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
            </svg>
          </span>
          <span class="platform-info-dialog__name">{{ APP_NAME }}</span>
        </div>

        <section class="platform-info-block">
          <h4 class="platform-info-block__title">系统说明</h4>
          <p class="platform-info-block__text">{{ platformIntro }}</p>
        </section>

        <section class="platform-info-block">
          <h4 class="platform-info-block__title">服务项目</h4>
          <p class="platform-info-block__text">{{ platformServiceText }}</p>
        </section>

        <section class="platform-info-block">
          <h4 class="platform-info-block__title">关于我们</h4>
          <p class="platform-info-block__text">{{ platformAboutText }}</p>
        </section>

        <section class="platform-info-block">
          <h4 class="platform-info-block__title">联系我们</h4>
          <p class="platform-info-block__text">
            客服邮箱：{{ platformContact.email }}<br />
            客服电话：{{ platformContact.phone }}
          </p>
        </section>

        <footer class="platform-info-dialog__footer">
          <p class="platform-info-dialog__copy">{{ platformCopyright }}</p>
          <p class="platform-info-dialog__extra">{{ platformExtraText }}</p>
          <p class="platform-info-dialog__tip">{{ platformFooterTip }}</p>
        </footer>
      </div>
    </el-dialog>
  </SiteLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Edit,
  Coin,
  Calendar,
  Document,
  ChatLineRound,
  Download,
  Lock,
  InfoFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import { useIsMobile } from '@/composables/useBreakpoints'
import { PLATFORM_INTRO, APP_NAME } from '@/config'
import {
  PLATFORM_SERVICE_LINKS,
  PLATFORM_ABOUT_LINKS,
  PLATFORM_CONTACT,
  PLATFORM_EXTRA_LINKS,
  PLATFORM_COPYRIGHT,
  PLATFORM_FOOTER_TIP,
} from '@/constants/siteLinks'
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
  acknowledgeHealthIntervention,
} from '@/api/user'
import {
  readHealthTrendCache,
  writeHealthTrendCache,
  clearHealthTrendCache,
} from '@/composables/useHealthTrendCache'
import { getCheckinStats } from '@/api/checkin'
import { useUserStore } from '@/stores/user'
import {
  isNotificationSupported,
  getNotificationPermission,
  requestNotificationPermission,
} from '@/utils/notification'
import {
  GENDER_LABELS,
  DIABETES_TYPE_LABELS,
  SMOKING_LABELS,
  EXERCISE_LABELS,
  DIET_LABELS,
} from './constants'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isMobile = useIsMobile()
const platformIntro = PLATFORM_INTRO
const platformContact = PLATFORM_CONTACT
const platformCopyright = PLATFORM_COPYRIGHT
const platformFooterTip = PLATFORM_FOOTER_TIP
const platformServiceText = PLATFORM_SERVICE_LINKS.map((item) => item.label).join('、')
const platformAboutText = PLATFORM_ABOUT_LINKS.map((item) => item.label).join('、')
const platformExtraText = PLATFORM_EXTRA_LINKS.map((item) => item.label).join(' · ')

const avatarSize = computed(() => (isMobile.value ? 56 : 80))

const checkinNotifyRef = ref(null)

const notificationSupported = computed(() => isNotificationSupported())
const browserNotifyLabel = computed(() => {
  if (!notificationSupported.value) return '不支持'
  const p = getNotificationPermission()
  if (p === 'granted') return '已授权'
  if (p === 'denied') return '已拒绝'
  return '未授权'
})

async function enableBrowserNotify() {
  const result = await requestNotificationPermission()
  if (result === 'granted') {
    ElMessage.success('浏览器通知已开启')
  } else if (result === 'denied') {
    ElMessage.warning('您已拒绝浏览器通知，请在浏览器设置中手动开启')
  } else if (result === 'unsupported') {
    ElMessage.warning('当前环境不支持浏览器通知')
  }
}

const pageLoading = ref(false)
const profile = ref(null)
const health = ref({})
const healthAlert = ref(null)
const trendSummary = ref('')
const trendLoading = ref(false)
const consultCount = ref(0)
const checkinStats = ref({})

const showProfileEdit = ref(false)
const showHealthEdit = ref(false)
const showExport = ref(false)
const showConsultations = ref(false)
const showSecurity = ref(false)
const showPlatformInfo = ref(false)

const privacy = reactive({
  data_visible: true,
  checkin_notify: true,
  message_notify: true,
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
    label: '咨询次数',
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
  { key: 'plan', label: '健康方案', icon: Document, path: '/living-plans'},
  { key: 'consult', label: '咨询记录', icon: ChatLineRound, action: 'consultations' }
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

onMounted(async () => {
  await loadPage()
  if (route.query.section === 'checkin-notify') {
    await nextTick()
    checkinNotifyRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
})

async function loadPage() {
  pageLoading.value = true
  try {
    const [profileRes, healthRes, alertRes, consultRes, statsRes] = await Promise.allSettled([
      getUserProfile(),
      getHealthRecord(),
      getHealthAlert(),
      getUserConsultations(),
      getCheckinStats(),
    ])

    if (profileRes.status === 'fulfilled') {
      profile.value = profileRes.value
      userStore.profile = profileRes.value
      const ps = profileRes.value.privacy_settings || {}
      privacy.data_visible = ps.data_visible ?? true
      privacy.checkin_notify = ps.checkin_notify ?? true
      privacy.message_notify = ps.message_notify ?? ps.consult_notify ?? true
      applyTrendCache(profileRes.value?.user_id)
    } else {
      console.error('加载用户资料失败', profileRes.reason)
    }

    if (healthRes.status === 'fulfilled') {
      health.value = healthRes.value
    } else {
      console.error('加载健康档案失败', healthRes.reason)
    }

    if (alertRes.status === 'fulfilled') {
      healthAlert.value = alertRes.value
    }

    if (consultRes.status === 'fulfilled') {
      consultCount.value = consultRes.value?.total ?? 0
    }

    if (statsRes.status === 'fulfilled') {
      checkinStats.value = statsRes.value
    } else {
      console.error('加载打卡统计失败', statsRes.reason)
    }
  } finally {
    pageLoading.value = false
  }
}

function applyTrendCache(userId) {
  const cached = readHealthTrendCache(userId)
  if (cached?.summary) {
    trendSummary.value = cached.summary
  }
}

async function refreshHealthTrend({ force = false } = {}) {
  const userId = profile.value?.user_id
  if (!userId) return
  trendLoading.value = true
  try {
    const trend = await getHealthTrendSummary(30, { force })
    if (trend?.summary) {
      trendSummary.value = trend.summary
      writeHealthTrendCache(userId, trend, health.value?.recorded_at)
    }
  } catch {
    /* 保留缓存或占位文案 */
  } finally {
    trendLoading.value = false
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
  clearHealthTrendCache(profile.value?.user_id)
  refreshHealthTrend({ force: true })
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

function goGlucoseCheckin() {
  router.push('/checkin-records/glucose')
}

async function ackHealthAlert() {
  const planId = healthAlert.value?.plan_id || healthAlert.value?.planId
  if (planId) {
    try {
      await acknowledgeHealthIntervention(planId)
    } catch {
      /* ignore */
    }
  }
  healthAlert.value = { ...healthAlert.value, has_alert: false, active: false }
}

async function handleLogout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.uc-page {
  padding-bottom: 64px;
}

/* 左右布局 */
.uc-layout {
  display: grid;
  grid-template-columns: minmax(280px, 320px) 1fr;
  gap: 24px;
  align-items: start;
}

.uc-sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: sticky;
  top: calc(var(--header-height) + 24px);
}

.uc-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.menu-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.menu-row__item {
  margin-bottom: 0;
}

.uc-main > .section-card:last-child {
  margin-bottom: 0;
}

/* 预警条 */
.alert-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  margin-bottom: 24px;
  border-radius: 12px;
}

.alert-banner--warning {
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.alert-banner--warning .alert-banner__icon {
  color: #f59e0b;
}

.alert-banner--warning .alert-banner__text {
  color: #92400e;
}

.alert-banner--error {
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.alert-banner--error .alert-banner__icon {
  color: #ef4444;
}

.alert-banner--error .alert-banner__text {
  color: #991b1b;
}

.alert-banner__icon {
  width: 20px;
  height: 20px;
  margin-top: 2px;
  flex-shrink: 0;
}

.alert-banner__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
}

.alert-banner__body {
  flex: 1;
  min-width: 0;
}

.alert-banner__sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: #78716c;
  line-height: 1.5;
}

.alert-banner__actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

/* 个人信息卡片 */
.profile-hero {
  position: relative;
  background: linear-gradient(to bottom, #0d9488, #14b8a6);
  border-radius: 16px;
  padding: 28px 24px;
  color: #fff;
  box-shadow: 0 10px 30px rgba(13, 148, 136, 0.2);
  text-align: center;
}

.profile-hero__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.profile-hero__user {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.profile-hero__avatar-wrap {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar {
  width: 80px;
  height: 80px;
}

.profile-text {
  min-width: 0;
}

.profile-name-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.profile-text h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
}

.username {
  margin: 0 0 12px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
}

.badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: #fff;
}

.profile-extra {
  margin: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.profile-edit-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: background-color 0.2s ease;
}

.profile-edit-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 统计概览 */
.stat-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.stat-card:active {
  transform: scale(0.98);
}

.stat-value {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 700;
  color: var(--health-600);
  line-height: 1.2;
}

.stat-label {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
}

/* 卡片区块 */
.uc-page :deep(.section-card) {
  border-radius: 12px;
  padding: 32px;
  margin-bottom: 24px;
  border: none;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title-icon {
  width: 20px;
  height: 20px;
  color: var(--health-500);
  flex-shrink: 0;
}

.uc-page .section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.menu-group-title {
  margin: 0 0 20px;
}

.privacy-title {
  margin-bottom: 24px;
}

.ai-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: var(--health-600);
  background: #f0fdfa;
}

.trend-text {
  margin: 0;
  font-size: 14px;
  color: #4b5563;
  line-height: 1.7;
}

.trend-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #9ca3af;
}

.text-link {
  border: none;
  background: none;
  padding: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--health-600);
  cursor: pointer;
  transition: color 0.2s ease;
}

.text-link:hover {
  color: var(--health-700);
}

/* 健康档案 */
.health-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 48px;
}

.health-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}

.health-label {
  font-size: 14px;
  color: #6b7280;
}

.health-value {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  text-align: right;
}

.health-value--muted {
  color: #9ca3af;
  font-weight: 400;
}

.record-time {
  margin: 24px 0 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: right;
}

.value-warn { color: #d97706; }
.value-normal { color: #059669; }

/* 菜单列表 */
.menu-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.menu-item:hover {
  background: #f9fafb;
}

.menu-item__left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.menu-item__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.menu-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.menu-icon--primary {
  background: #f0fdfa;
  color: var(--health-500);
}

.menu-icon--gray {
  background: #f9fafb;
  color: #6b7280;
}

.menu-label {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
}

.menu-desc {
  font-size: 12px;
  color: #9ca3af;
}

.menu-item .arrow {
  color: #9ca3af;
  flex-shrink: 0;
  transition: color 0.2s ease;
}

.menu-item:hover .arrow {
  color: #4b5563;
}

/* 隐私开关 */
.switch-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.switch-row--highlight {
  scroll-margin-top: 96px;
}

.switch-links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
}

.switch-label {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  margin-bottom: 4px;
}

.switch-desc {
  font-size: 12px;
  color: #9ca3af;
}

.uc-page :deep(.el-switch.is-checked .el-switch__core) {
  background-color: var(--health-500);
  border-color: var(--health-500);
}

/* 退出登录 */
.logout-btn {
  width: 100%;
  padding: 14px;
  border: 2px solid #f87171;
  border-radius: 8px;
  background: transparent;
  color: #ef4444;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.logout-btn:hover {
  background: #fef2f2;
}

.platform-info-dialog__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.platform-info-dialog__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--health-400), var(--health-600));
  color: #fff;
}

.platform-info-dialog__logo svg {
  width: 20px;
  height: 20px;
}

.platform-info-dialog__name {
  font-size: 18px;
  font-weight: 700;
  color: var(--warm-800);
}

.platform-info-block + .platform-info-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--warm-100);
}

.platform-info-block__title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--warm-500);
}

.platform-info-block__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--warm-600);
}

.platform-info-dialog__footer {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--warm-100);
  text-align: center;
}

.platform-info-dialog__copy,
.platform-info-dialog__extra,
.platform-info-dialog__tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--warm-400);
}

.platform-info-dialog__extra {
  margin-top: 8px;
}

.platform-info-dialog__tip {
  margin-top: 12px;
  color: var(--warm-300);
}

@media (max-width: 1024px) {
  .uc-layout {
    grid-template-columns: 1fr;
  }

  .uc-sidebar {
    position: static;
  }

  .menu-row {
    grid-template-columns: 1fr;
  }

  .menu-row__item {
    margin-bottom: 24px;
  }

  .menu-row__item:last-child {
    margin-bottom: 0;
  }
}

@media (max-width: 768px) {
  .uc-page {
    padding-bottom: 72px;
  }

  .uc-layout {
    gap: 12px;
  }

  .uc-sidebar {
    gap: 10px;
  }

  .uc-main {
    gap: 0;
  }

  .alert-banner {
    padding: 10px 12px;
    margin-bottom: 12px;
    border-radius: 10px;
    gap: 8px;
  }

  .alert-banner__icon {
    width: 18px;
    height: 18px;
  }

  .alert-banner__text {
    font-size: 13px;
    line-height: 1.45;
  }

  .profile-hero {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    border-radius: 12px;
    text-align: left;
    box-shadow: 0 4px 16px rgba(13, 148, 136, 0.15);
  }

  .profile-hero__avatar-wrap {
    width: 56px;
    height: 56px;
    margin: 0;
  }

  .avatar {
    width: 56px;
    height: 56px;
  }

  .profile-text {
    flex: 1;
    min-width: 0;
  }

  .profile-text h2 {
    font-size: 17px;
    margin-bottom: 2px;
  }

  .username {
    margin-bottom: 6px;
    font-size: 12px;
  }

  .badges {
    justify-content: flex-start;
    gap: 6px;
    margin-bottom: 4px;
  }

  .badge {
    padding: 2px 8px;
    font-size: 11px;
  }

  .profile-extra {
    font-size: 11px;
    line-height: 1.4;
  }

  .profile-edit-btn {
    top: 10px;
    right: 10px;
    width: 30px;
    height: 30px;
  }

  .stat-stack {
    flex-direction: row;
    gap: 8px;
  }

  .stat-card {
    flex: 1;
    min-width: 0;
    padding: 10px 6px;
    border-radius: 10px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  }

  .stat-value {
    font-size: 17px;
    margin-bottom: 2px;
  }

  .stat-label {
    font-size: 11px;
    line-height: 1.3;
  }

  .logout-btn {
    padding: 10px;
    font-size: 13px;
    border-radius: 10px;
    border-width: 1px;
  }

  .uc-page :deep(.section-card) {
    padding: 14px;
    margin-bottom: 10px;
    border-radius: 12px;
    box-shadow: 0 1px 8px rgba(0, 0, 0, 0.04);
  }

  .section-header {
    margin-bottom: 10px;
  }

  .uc-page .section-title {
    font-size: 15px;
  }

  .menu-group-title,
  .privacy-title {
    margin-bottom: 10px;
  }

  .section-title-icon {
    width: 18px;
    height: 18px;
  }

  .ai-tag {
    padding: 2px 8px;
    font-size: 11px;
  }

  .trend-text {
    font-size: 13px;
    line-height: 1.55;
  }

  .text-link {
    font-size: 13px;
  }

  .health-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .health-row {
    padding: 8px 0;
  }

  .health-label,
  .health-value {
    font-size: 13px;
  }

  .record-time {
    margin-top: 10px;
    font-size: 11px;
  }

  .menu-row {
    gap: 10px;
  }

  .menu-row__item {
    margin-bottom: 0;
  }

  .menu-list {
    gap: 4px;
  }

  .menu-item {
    padding: 10px 8px;
    border-radius: 10px;
  }

  .menu-item__left {
    gap: 10px;
  }

  .menu-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
  }

  .menu-icon :deep(.el-icon) {
    font-size: 17px !important;
  }

  .menu-label {
    font-size: 13px;
  }

  .menu-item__right .menu-desc {
    display: none;
  }

  .switch-list {
    gap: 14px;
  }

  .switch-row {
    align-items: flex-start;
    gap: 10px;
  }

  .switch-label {
    font-size: 13px;
    margin-bottom: 2px;
  }

  .switch-desc {
    font-size: 11px;
    line-height: 1.45;
  }

  .switch-links {
    gap: 8px;
    margin-top: 4px;
  }

  .uc-page :deep(.el-switch) {
    height: 20px;
  }
}
</style>
