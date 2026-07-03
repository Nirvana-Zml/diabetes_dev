<template>
  <SiteLayout title="生活打卡" full-bleed>
    <div class="checkin-hub" :class="{ 'checkin-hub--mobile': isMobile }">
      <div v-if="bannerReminders.length" class="reminder-banner-wrap">
        <el-alert
          v-for="item in bannerReminders"
          :key="item.log_id || item.logId"
          type="warning"
          :closable="true"
          show-icon
          class="reminder-banner"
          @close="dismissBanner(item)"
        >
          <template #title>{{ item.title || item.checkin_type_label + '提醒' }}</template>
          <div class="reminder-banner__body">
            <span>{{ item.body }}</span>
            <el-button type="primary" size="small" @click="goReminderTab(item)">立即打卡</el-button>
          </div>
        </el-alert>
      </div>

      <section v-if="isMobile" class="stats-grid stats-grid--mobile">
        <div class="stat-card stat-card--mobile">
          <div class="stat-card__mobile-head">
            <div class="stat-icon stat-icon--primary"><el-icon :size="14"><DocumentChecked /></el-icon></div>
            <span class="stat-badge stat-badge--primary">+{{ stats.week_checkins }} 本周</span>
          </div>
          <p class="stat-value">{{ stats.total_checkins }}</p>
          <p class="stat-label">总卡数量</p>
        </div>
        <div class="stat-card stat-card--mobile">
          <div class="stat-card__mobile-head">
            <div class="stat-icon stat-icon--accent"><el-icon :size="14"><Star /></el-icon></div>
            <span class="stat-badge stat-badge--accent">+{{ todayStatus.today_points || 0 }} 今日</span>
          </div>
          <p class="stat-value">{{ stats.total_points.toLocaleString() }}</p>
          <p class="stat-label">总积分</p>
        </div>
        <div class="stat-card stat-card--mobile">
          <div class="stat-card__mobile-head">
            <div class="stat-icon stat-icon--orange"><el-icon :size="14"><Sunny /></el-icon></div>
            <span v-if="stats.streak_days >= 7" class="stat-badge stat-badge--orange">火热</span>
          </div>
          <p class="stat-value">{{ stats.streak_days }}</p>
          <p class="stat-label">连续打卡</p>
        </div>
      </section>

      <section v-else class="stats-grid">
        <div class="stat-card">
          <div class="stat-card__top">
            <div>
              <div class="stat-icon stat-icon--primary"><el-icon :size="18"><DocumentChecked /></el-icon></div>
              <p class="stat-value">{{ stats.total_checkins }}</p>
              <p class="stat-label">总卡数量</p>
            </div>
            <span class="stat-badge stat-badge--primary">+{{ stats.week_checkins }} 本周</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__top">
            <div>
              <div class="stat-icon stat-icon--accent"><el-icon :size="18"><Star /></el-icon></div>
              <p class="stat-value">{{ stats.total_points.toLocaleString() }}</p>
              <p class="stat-label">总积分</p>
            </div>
            <span class="stat-badge stat-badge--accent">+{{ todayStatus.today_points || 0 }} 今日</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__top">
            <div>
              <div class="stat-icon stat-icon--orange"><el-icon :size="18"><Sunny /></el-icon></div>
              <p class="stat-value">{{ stats.streak_days }}</p>
              <p class="stat-label">连续打卡</p>
            </div>
            <span v-if="stats.streak_days >= 7" class="stat-badge stat-badge--orange">火热</span>
          </div>
        </div>
        <div class="stat-card stat-card--progress">
          <div class="progress-ring-wrap">
            <svg class="progress-ring" viewBox="0 0 64 64">
              <circle cx="32" cy="32" r="28" fill="none" stroke="#d1fae5" stroke-width="6" />
              <circle
                cx="32" cy="32" r="28" fill="none" stroke="#10b981" stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="progressCircumference"
                :stroke-dashoffset="progressRingOffset"
                class="progress-ring-circle"
              />
            </svg>
            <span class="progress-ring-text">{{ todayProgress }}%</span>
          </div>
          <div>
            <p class="progress-title">今日完成</p>
            <p class="progress-sub">{{ todayTasksSummary.done }}/{{ todayTasksSummary.total }} 项任务</p>
          </div>
        </div>
      </section>

      <div v-if="isMobile" class="mobile-hero-row">
        <div class="mobile-today-progress">
          <div class="mobile-today-progress__info">
            <div class="mobile-today-progress__ring">
              <svg viewBox="0 0 40 40" aria-hidden="true">
                <circle cx="20" cy="20" r="16" fill="none" stroke="#d1fae5" stroke-width="4" />
                <circle
                  cx="20" cy="20" r="16" fill="none" stroke="url(#progressGradient)" stroke-width="4"
                  stroke-linecap="round"
                  :stroke-dasharray="mobileRingCircumference"
                  :stroke-dashoffset="mobileRingOffset"
                  transform="rotate(-90 20 20)"
                />
                <defs>
                  <linearGradient id="progressGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stop-color="#10b981" />
                    <stop offset="100%" stop-color="#f59e0b" />
                  </linearGradient>
                </defs>
              </svg>
              <span>{{ todayProgress }}%</span>
            </div>
            <div class="mobile-today-progress__text">
              <strong>今日打卡进度</strong>
              <span>{{ todayTasksSummary.done }}/{{ todayTasksSummary.total }} 项任务已完成</span>
            </div>
          </div>
          <div class="mobile-today-progress__bar">
            <div class="mobile-today-progress__fill" :style="{ width: `${todayProgress}%` }" />
          </div>
        </div>

        <button
          type="button"
          class="mobile-achievement-card"
          aria-label="查看成就墙"
          @click="$router.push('/checkin-records/achievements')"
        >
          <div class="mobile-achievement-card__glow" />
          <div class="mobile-achievement-card__icon">
            <el-icon :size="22"><Trophy /></el-icon>
          </div>
          <span class="mobile-achievement-card__label">成就墙</span>
          <div class="mobile-achievement-card__count">
            <span class="mobile-achievement-card__num">{{ unlockedCount }}</span>
            <span class="mobile-achievement-card__total">/{{ achievementTotal }}</span>
          </div>
          <div class="mobile-achievement-card__bar">
            <div
              class="mobile-achievement-card__bar-fill"
              :style="{ width: `${achievementPercent}%` }"
            />
          </div>
          <div v-if="achievementPreviewEmojis.length" class="mobile-achievement-card__emojis">
            <span v-for="(emoji, i) in achievementPreviewEmojis" :key="i">{{ emoji }}</span>
          </div>
        </button>
      </div>

      <nav v-if="isMobile" class="mobile-checkin-nav" aria-label="打卡相关功能">
        <button type="button" class="mobile-nav-card mobile-nav-card--analysis" @click="$router.push('/checkin-analysis')">
          <span class="mobile-nav-card__icon"><el-icon :size="20"><DataAnalysis /></el-icon></span>
          <span class="mobile-nav-card__body">
            <span class="mobile-nav-card__label">打卡分析</span>
            <span class="mobile-nav-card__hint">趋势与 AI 总结</span>
          </span>
          <el-icon class="mobile-nav-card__arrow"><ArrowRight /></el-icon>
        </button>
        <button type="button" class="mobile-nav-card mobile-nav-card--reminder" @click="$router.push('/checkin-reminder-settings')">
          <span class="mobile-nav-card__icon"><el-icon :size="20"><Bell /></el-icon></span>
          <span class="mobile-nav-card__body">
            <span class="mobile-nav-card__label">提醒设置</span>
            <span class="mobile-nav-card__hint">定时打卡提醒</span>
          </span>
          <el-icon class="mobile-nav-card__arrow"><ArrowRight /></el-icon>
        </button>
      </nav>

      <div class="hub-body">
        <aside v-if="!isMobile" class="hub-aside">
          <section class="analysis-banner" @click="$router.push('/checkin-analysis')">
            <div class="banner-left">
              <div class="banner-icon"><el-icon :size="24"><DataAnalysis /></el-icon></div>
              <div class="banner-text">
                <strong>打卡统计分析</strong>
                <span>查看周/月趋势与 AI 行为总结</span>
              </div>
            </div>
            <div class="banner-right">
              <span>查看详情</span>
              <div class="banner-arrow"><el-icon><ArrowRight /></el-icon></div>
            </div>
          </section>

          <section class="achievement-preview">
            <div class="achievement-preview__head">
              <h3>成就墙</h3>
              <div class="ach-count">
                <span class="ach-count-done">{{ unlockedCount }}</span>
                <span class="ach-count-sep">/</span>
                <span class="ach-count-total">{{ achievementTotal }}</span>
              </div>
            </div>
            <div class="achievement-grid">
              <div
                v-for="a in achievementPreview"
                :key="a.id"
                class="achievement-card"
                :class="{ unlocked: a.unlocked }"
              >
                <div class="achievement-icon"><span>{{ a.unlocked ? a.emoji : '🔒' }}</span></div>
                <h4 class="ach-name">{{ a.name }}</h4>
                <p v-if="a.desc" class="ach-desc">{{ a.desc }}</p>
              </div>
            </div>
            <button type="button" class="achievement-preview__link" @click="$router.push('/checkin-records/achievements')">
              查看全部成就
              <el-icon><ArrowRight /></el-icon>
            </button>
          </section>
        </aside>

        <section class="type-cards-panel">
          <h2 class="type-cards-title">选择打卡类型</h2>
          <div class="type-cards-grid">
            <button
              v-for="t in CHECKIN_TYPES"
              :key="t.key"
              type="button"
              class="type-card"
              :style="{ '--type-color': t.color }"
              @click="goTypePage(t.key)"
            >
              <span class="type-card__icon" :style="{ background: `${t.color}18`, color: t.color }">
                <el-icon :size="24"><component :is="t.icon" /></el-icon>
              </span>
              <span class="type-card__body">
                <strong>{{ t.label }}</strong>
                <span>{{ t.desc }}</span>
              </span>
              <span v-if="isTypeCompleted(t.key)" class="type-card__badge">已完成</span>
              <el-icon class="type-card__arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </section>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight, DataAnalysis, DocumentChecked, Star, Sunny, Bell, Trophy,
} from '@element-plus/icons-vue'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import { useIsMobile } from '@/composables/useBreakpoints'
import { getTodayStatus, getCheckinStats, getAchievementWall } from '@/api/checkin'
import { pendingReminders, useCheckinReminder } from '@/composables/useCheckinReminder'
import { ackReminder, clickReminder } from '@/api/checkinReminder'
import { CHECKIN_TYPES, CHECKIN_STATUS_TYPE } from './checkin/constants'

const router = useRouter()
const isMobile = useIsMobile()
const { refresh: refreshReminders } = useCheckinReminder()

const stats = ref({ total_points: 0, streak_days: 0, total_checkins: 0, week_checkins: 0 })
const todayStatus = ref({ today_checkins: [], today_points: 0 })
const achievementWall = ref({ achievements: [], total: 0, unlockedCount: 0 })

const progressCircumference = 2 * Math.PI * 28
const mobileRingCircumference = 2 * Math.PI * 16

const bannerReminders = computed(() => pendingReminders.value || [])

const todayProgress = computed(() => {
  const list = todayStatus.value.today_checkins || []
  const done = list.filter((x) => x.completed).length
  return Math.round((done / 4) * 100)
})

const todayTasksSummary = computed(() => {
  const list = todayStatus.value.today_checkins || []
  const done = list.filter((x) => x.completed).length
  return { done, total: Math.max(list.length, 4) }
})

const progressRingOffset = computed(() => progressCircumference * (1 - todayProgress.value / 100))
const mobileRingOffset = computed(() => mobileRingCircumference * (1 - todayProgress.value / 100))
const unlockedCount = computed(() => achievementWall.value.unlockedCount)
const achievementTotal = computed(() => achievementWall.value.total)
const achievementPreview = computed(() => achievementWall.value.achievements.slice(0, 4))
const achievementPercent = computed(() => {
  const total = achievementTotal.value
  return total ? Math.round((unlockedCount.value / total) * 100) : 0
})
const achievementPreviewEmojis = computed(() =>
  achievementWall.value.achievements
    .filter((a) => a.unlocked)
    .slice(0, 3)
    .map((a) => a.emoji),
)

onMounted(async () => {
  await loadHeroData()
  refreshReminders()
})

async function loadHeroData() {
  const [status, st, wall] = await Promise.all([getTodayStatus(), getCheckinStats(), getAchievementWall()])
  todayStatus.value = status
  stats.value = {
    total_points: st.total_points ?? status.total_points ?? 0,
    streak_days: st.streak_days ?? status.streak_days ?? 0,
    total_checkins: st.total_checkins ?? 0,
    week_checkins: st.week_checkins ?? 0,
  }
  achievementWall.value = wall
}

function isTypeCompleted(key) {
  const list = todayStatus.value.today_checkins || []
  const statusKey = CHECKIN_STATUS_TYPE[key] || key
  return list.some((x) => (x.checkin_type || x.checkinType) === statusKey && x.completed)
}

function goTypePage(key) {
  router.push(`/checkin-records/${key}`)
}

async function goReminderTab(item) {
  const tab = item.tab || 'food'
  const logId = item.log_id || item.logId
  if (logId) {
    try { await clickReminder(logId) } catch { /* ignore */ }
  }
  router.push(`/checkin-records/${tab}`)
}

function dismissBanner(item) {
  const logId = item.log_id || item.logId
  if (logId) ackReminder(logId).catch(() => {})
}
</script>

<style scoped>
.checkin-hub {
  --ck-bg: #f4f6f8;
  --ck-emerald: #10b981;
  --ck-emerald-dark: #059669;
  --ck-emerald-light: #ecfdf5;
  --ck-emerald-ring: #d1fae5;
  --ck-gold: #f59e0b;
  --ck-gold-light: #fffbeb;
  --ck-text: #1e293b;
  --ck-text-muted: #64748b;
  --ck-card: #ffffff;
  --ck-border: rgba(226, 232, 240, 0.9);
  --ck-inactive: #f1f5f9;
  --ck-shadow: 0 4px 20px rgba(15, 23, 42, 0.06);
  --ck-shadow-soft: 0 2px 8px rgba(15, 23, 42, 0.04);
  min-height: 100%;
  background: var(--ck-bg);
  padding: 24px clamp(16px, 2vw, 32px) 32px;
}

.reminder-banner-wrap {
  max-width: 1200px;
  margin: 0 auto 12px;
}

.reminder-banner { margin-bottom: 8px; }

.reminder-banner__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 24px;
  max-width: 1200px;
  margin-left: auto;
  margin-right: auto;
}

.stats-grid--mobile {
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.stat-card {
  background: var(--ck-card);
  border-radius: 18px;
  padding: 20px;
  box-shadow: var(--ck-shadow-soft);
  border: 1px solid var(--ck-border);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card--mobile {
  padding: 14px 12px;
  border-radius: 16px;
  background: linear-gradient(160deg, #ffffff 0%, #f8fafc 100%);
}

.stat-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.stat-card__mobile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.checkin-hub--mobile .stat-icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  margin-bottom: 0;
}

.stat-icon--primary { background: linear-gradient(135deg, #ecfdf5, #d1fae5); color: var(--ck-emerald); }
.stat-icon--accent { background: linear-gradient(135deg, #fffbeb, #fef3c7); color: var(--ck-gold); }
.stat-icon--orange { background: linear-gradient(135deg, #fff7ed, #ffedd5); color: #f97316; }

.stat-value {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--ck-text);
  line-height: 1.1;
}

.checkin-hub--mobile .stat-value { font-size: 22px; }

.stat-label {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ck-text-muted);
}

.stat-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  white-space: nowrap;
}

.stat-badge--primary { color: var(--ck-emerald-dark); background: var(--ck-emerald-light); }
.stat-badge--accent { color: #b45309; background: var(--ck-gold-light); }
.stat-badge--orange { color: #c2410c; background: #fff7ed; }

.stat-card--progress {
  display: flex;
  align-items: center;
  gap: 20px;
}

.progress-ring-wrap {
  position: relative;
  width: 64px;
  height: 64px;
  flex-shrink: 0;
}

.progress-ring {
  transform: rotate(-90deg);
  width: 64px;
  height: 64px;
}

.progress-ring-circle { transition: stroke-dashoffset 0.5s ease; }

.progress-ring-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--ck-emerald);
}

.progress-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--ck-text);
}

.progress-sub {
  margin: 2px 0 0;
  font-size: 14px;
  color: var(--ck-text-muted);
}

.mobile-hero-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 118px;
  gap: 10px;
  margin-bottom: 12px;
}

.mobile-today-progress {
  background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
  border-radius: 18px;
  padding: 16px;
  box-shadow: var(--ck-shadow-soft);
  border: 1px solid var(--ck-border);
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 132px;
}

.mobile-today-progress__info {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

.mobile-today-progress__ring {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
}

.mobile-today-progress__ring svg { width: 44px; height: 44px; }

.mobile-today-progress__ring span {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  color: var(--ck-emerald-dark);
}

.mobile-today-progress__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mobile-today-progress__text strong {
  font-size: 15px;
  font-weight: 700;
  color: var(--ck-text);
  letter-spacing: -0.01em;
}

.mobile-today-progress__text span {
  font-size: 12px;
  color: var(--ck-text-muted);
  line-height: 1.4;
}

.mobile-today-progress__bar {
  height: 6px;
  border-radius: 999px;
  background: var(--ck-emerald-ring);
  overflow: hidden;
}

.mobile-today-progress__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--ck-emerald), var(--ck-gold));
  transition: width 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.35);
}

.mobile-achievement-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 12px 10px;
  border: 1px solid rgba(251, 191, 36, 0.35);
  border-radius: 18px;
  background: linear-gradient(160deg, #fffbeb 0%, #fef3c7 45%, #fff7ed 100%);
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.15);
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.mobile-achievement-card:active {
  transform: scale(0.97);
}

.mobile-achievement-card__glow {
  position: absolute;
  top: -20px;
  right: -20px;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(251, 191, 36, 0.25);
  filter: blur(16px);
  pointer-events: none;
}

.mobile-achievement-card__icon {
  position: relative;
  z-index: 1;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fbbf24, #f97316);
  color: #fff;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.4);
}

.mobile-achievement-card__label {
  position: relative;
  z-index: 1;
  font-size: 12px;
  font-weight: 700;
  color: #92400e;
}

.mobile-achievement-card__count {
  position: relative;
  z-index: 1;
  line-height: 1;
}

.mobile-achievement-card__num {
  font-size: 20px;
  font-weight: 900;
  color: #b45309;
}

.mobile-achievement-card__total {
  font-size: 13px;
  font-weight: 600;
  color: #d97706;
}

.mobile-achievement-card__bar {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 4px;
  margin-top: 2px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.6);
  overflow: hidden;
}

.mobile-achievement-card__bar-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #f59e0b, #f97316);
  transition: width 0.5s ease;
}

.mobile-achievement-card__emojis {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 2px;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1;
}

.mobile-checkin-nav {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.hub-body {
  display: grid;
  grid-template-columns: minmax(260px, 300px) minmax(0, 1fr);
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
  align-items: start;
}

.checkin-hub--mobile .hub-body {
  grid-template-columns: 1fr;
}

.hub-aside {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: sticky;
  top: 24px;
}

.analysis-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
  border-radius: 20px;
  cursor: pointer;
  border: 1px solid var(--ck-emerald-ring);
}

.banner-left { display: flex; align-items: center; gap: 14px; }

.banner-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #fff;
  color: var(--ck-emerald);
  display: flex;
  align-items: center;
  justify-content: center;
}

.banner-text { display: flex; flex-direction: column; gap: 4px; }
.banner-text strong { font-size: 16px; color: var(--ck-text); }
.banner-text span { font-size: 13px; color: var(--ck-text-muted); }

.banner-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ck-emerald);
}

.banner-arrow {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.achievement-preview {
  background: var(--ck-card);
  border-radius: 20px;
  padding: 20px;
  box-shadow: var(--ck-shadow);
  border: 1px solid var(--ck-border);
}

.achievement-preview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.achievement-preview__head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.achievement-preview__link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  margin-top: 16px;
  padding: 10px;
  border: none;
  border-radius: 12px;
  background: var(--ck-emerald-light);
  color: var(--ck-emerald);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.achievement-preview__link:hover {
  background: var(--ck-emerald-ring);
}

.ach-count { font-size: 14px; color: var(--ck-text-muted); }
.ach-count-done { color: var(--ck-emerald); font-weight: 700; }

.achievement-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-top: 16px;
}

.achievement-card {
  padding: 14px;
  border-radius: 14px;
  background: var(--ck-inactive);
  text-align: center;
  opacity: 0.65;
}

.achievement-card.unlocked { opacity: 1; }
.achievement-card.tone-0 { background: #ecfdf5; }
.achievement-card.tone-1 { background: #eff6ff; }
.achievement-card.tone-2 { background: #fffbeb; }

.achievement-icon { font-size: 24px; margin-bottom: 6px; }

.ach-name {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 600;
}

.ach-desc {
  margin: 0 0 8px;
  font-size: 11px;
  color: var(--ck-text-muted);
  line-height: 1.4;
}

.ach-status span {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255,255,255,0.7);
}

.type-cards-panel {
  background: var(--ck-card);
  border-radius: 20px;
  padding: 24px;
  box-shadow: var(--ck-shadow);
  border: 1px solid var(--ck-border);
}

.type-cards-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 700;
  color: var(--ck-text);
}

.type-cards-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.checkin-hub--mobile .type-cards-grid {
  grid-template-columns: 1fr;
}

.checkin-hub--mobile .type-cards-panel {
  border-radius: 18px;
  padding: 18px;
  box-shadow: var(--ck-shadow-soft);
}

.checkin-hub--mobile .type-card {
  background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
  border-color: var(--ck-border);
}

.type-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--ck-border);
  border-radius: 16px;
  background: #fafaf9;
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
}

.type-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.type-card:active { transform: scale(0.98); }

.type-card__icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.type-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.type-card__body strong {
  font-size: 16px;
  color: var(--ck-text);
}

.type-card__body span {
  font-size: 13px;
  color: var(--ck-text-muted);
}

.type-card__badge {
  position: absolute;
  top: 12px;
  right: 36px;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--ck-emerald-light);
  color: var(--ck-emerald);
}

.type-card__arrow {
  color: var(--ck-text-muted);
  flex-shrink: 0;
}

.mobile-nav-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--ck-border);
  border-radius: 16px;
  background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: var(--ck-shadow-soft);
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.mobile-nav-card:active {
  transform: scale(0.98);
}

.mobile-nav-card--analysis {
  border-color: rgba(16, 185, 129, 0.2);
  background: linear-gradient(145deg, #ffffff 0%, #ecfdf5 100%);
}

.mobile-nav-card--reminder {
  border-color: rgba(99, 102, 241, 0.2);
  background: linear-gradient(145deg, #ffffff 0%, #eef2ff 100%);
}

.mobile-nav-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--ck-emerald-light);
  color: var(--ck-emerald);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.mobile-nav-card--reminder .mobile-nav-card__icon {
  background: #eef2ff;
  color: #6366f1;
}

.mobile-nav-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mobile-nav-card__label {
  font-size: 14px;
  font-weight: 700;
  color: var(--ck-text);
}

.mobile-nav-card__hint {
  font-size: 11px;
  color: var(--ck-text-muted);
}

.mobile-nav-card__arrow {
  flex-shrink: 0;
  color: var(--ck-text-muted);
  font-size: 14px;
}

@media (min-width: 769px) {
  .mobile-checkin-nav,
  .mobile-hero-row {
    display: none;
  }
}
</style>
