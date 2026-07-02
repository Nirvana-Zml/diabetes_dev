<template>
  <SiteLayout title="健康方案" full-bleed>

    <div class="page-container plan-page" :class="{ 'plan-page--mobile': isMobile }">
      <!-- 顶部操作区 -->
      <div class="section-card hero-card">
        <div v-if="isMobile" class="hero-card__decor" aria-hidden="true" />
        <div class="hero-text">
          <p v-if="isMobile" class="hero-eyebrow">AI 定制 · 饮食 · 运动 · 作息</p>
          <h2>{{ isMobile ? '我的健康方案' : '个性化健康管理方案' }}</h2>
          <div v-if="isMobile && plan?.summary" class="hero-summary-wrap">
            <p class="hero-summary" :class="{ 'hero-summary--collapsed': !summaryExpanded }">{{ plan.summary }}</p>
            <button
              v-if="summaryNeedsToggle"
              type="button"
              class="hero-summary-toggle"
              @click="summaryExpanded = !summaryExpanded"
            >
              {{ summaryExpanded ? '收起' : '展开全文' }}
            </button>
          </div>
          <p v-else-if="plan?.summary">{{ plan.summary }}</p>
          <p v-else class="muted">结合健康档案、风险评估与打卡数据，AI 为您定制饮食、运动与作息计划</p>
          <div v-if="isMobile && (displayPlan?.daily_calories || streamingCalories)" class="hero-stats-row">
            <div class="hero-calorie-pill">
              <span class="hero-calorie-pill__label">每日推荐</span>
              <div class="hero-calorie-pill__main">
                <span class="hero-calorie-pill__value">{{ displayPlan?.daily_calories || streamingCalories }}</span>
                <span class="hero-calorie-pill__unit">千卡</span>
              </div>
            </div>
            <div v-if="displayPlan?.version || displayPlan?.generated_at" class="hero-meta-stack">
              <span v-if="displayPlan?.version" class="hero-meta-chip">v{{ displayPlan.version }}</span>
              <span v-if="displayPlan?.generated_at" class="hero-meta-time">{{ formatShortTime(displayPlan.generated_at) }}</span>
            </div>
          </div>
        </div>
        <div class="hero-actions" :class="{ 'hero-actions--mobile': isMobile }">
          <el-button
            type="primary"
            :size="isMobile ? 'default' : 'large'"
            class="hero-generate-btn"
            :loading="generating"
            @click="handleGenerate"
          >
            <el-icon v-if="!generating"><MagicStick /></el-icon>
            {{ plan ? '重新生成' : '生成健康方案' }}
          </el-button>
          <div v-if="plan && isMobile" class="hero-secondary-row">
            <button
              type="button"
              class="hero-secondary-btn"
              :class="{ 'hero-secondary-btn--active': isPlanFavorited }"
              :disabled="favoriteLoading"
              @click="handleFavorite"
            >
              <el-icon><component :is="isPlanFavorited ? StarFilled : Star" /></el-icon>
              <span>{{ isPlanFavorited ? '已收藏' : '收藏' }}</span>
            </button>
            <button type="button" class="hero-secondary-btn" @click="handlePrint">
              <el-icon><Printer /></el-icon>
              <span>导出</span>
            </button>
          </div>
          <template v-else-if="plan">
            <el-button
              :size="isMobile ? 'default' : 'large'"
              :type="isPlanFavorited ? 'warning' : 'default'"
              :plain="isPlanFavorited"
              :loading="favoriteLoading"
              @click="handleFavorite"
            >
              <el-icon><component :is="isPlanFavorited ? StarFilled : Star" /></el-icon>
              {{ isPlanFavorited ? '已收藏' : '收藏' }}
            </el-button>
            <el-button :size="isMobile ? 'default' : 'large'" @click="handlePrint">
              <el-icon><Printer /></el-icon>
              导出
            </el-button>
          </template>
        </div>
      </div>

      <!-- 手机端区块导航 -->
      <nav
        v-if="isMobile && displayPlan && sectionNavItems.length > 1"
        class="mobile-section-nav"
        aria-label="方案内容导航"
      >
        <div class="mobile-section-nav__track">
          <button
            v-for="item in sectionNavItems"
            :key="item.id"
            type="button"
            class="mobile-section-nav__chip"
            :class="{ active: activeSection === item.id }"
            @click="scrollToSection(item.id)"
          >
            <span class="mobile-section-nav__icon" aria-hidden="true">{{ item.icon }}</span>
            {{ item.label }}
          </button>
        </div>
      </nav>

      <!-- 生成进度 -->
      <div v-if="generating" class="section-card progress-card">
        <template v-if="isMobile">
          <p class="mobile-progress-title">正在生成方案…</p>
          <div class="mobile-gen-steps">
            <div
              v-for="(step, index) in genStepLabels"
              :key="step.key"
              class="mobile-gen-step"
              :class="{ active: genStep === index, done: genStep > index }"
            >
              <span class="mobile-gen-step__dot">{{ genStep > index ? '✓' : index + 1 }}</span>
              <div class="mobile-gen-step__text">
                <strong>{{ step.title }}</strong>
                <span>{{ step.desc }}</span>
              </div>
            </div>
          </div>
        </template>
        <el-steps v-else :active="genStep" finish-status="success" align-center>
          <el-step title="热量计算" description="Mifflin-St Jeor" />
          <el-step title="饮食方案" description="三餐+加餐" />
          <el-step title="运动方案" description="有氧+力量" />
          <el-step title="作息方案" description="睡眠+监测" />
        </el-steps>
        <el-progress :percentage="genProgress" :stroke-width="isMobile ? 8 : 10" striped striped-flow />
        <p v-if="streamingCalories" class="stream-tip">
          每日推荐摄入：<strong>{{ streamingCalories }}</strong> 千卡
        </p>
      </div>

      <!-- 方案内容 -->
      <template v-if="displayPlan">
        <!-- 概览 -->
        <div id="plan-overview" class="section-card overview-card">
          <div v-if="isMobile" class="mobile-overview">
            <div class="mobile-overview__head">
              <h3>方案完成度</h3>
              <span class="mobile-overview__badge">{{ readyDimCount }}/{{ dimensions.length }}</span>
            </div>
            <div class="mobile-overview__track" aria-hidden="true">
              <div class="mobile-overview__fill" :style="{ width: `${overallPlanPercent}%` }" />
            </div>
            <div class="mobile-dim-grid">
              <button
                v-for="dim in dimensions"
                :key="dim.key"
                type="button"
                class="mobile-dim-card"
                :class="{ ready: dim.ready }"
                @click="scrollToDimSection(dim.key)"
              >
                <span class="mobile-dim-card__emoji" aria-hidden="true">{{ DIM_EMOJI[dim.key] }}</span>
                <div class="mobile-dim-card__body">
                  <strong>{{ dim.label }}</strong>
                  <span class="mobile-dim-card__status">{{ dim.ready ? '已生成' : '待完善' }}</span>
                </div>
                <div class="mobile-dim-card__bar" aria-hidden="true">
                  <div class="mobile-dim-card__bar-fill" :style="{ width: `${dim.percent}%`, background: dim.color }" />
                </div>
              </button>
            </div>
          </div>
          <el-row v-else :gutter="20">
            <el-col :xs="12" :sm="6" v-for="dim in dimensions" :key="dim.key">
              <div class="dim-card" :class="{ active: dim.ready }">
                <el-progress
                  type="circle"
                  :percentage="dim.percent"
                  :width="72"
                  :color="dim.color"
                  :status="dim.ready ? 'success' : undefined"
                />
                <span class="dim-label">{{ dim.label }}</span>
              </div>
            </el-col>
          </el-row>
          <div v-if="!isMobile || !(displayPlan?.daily_calories || streamingCalories)" class="calorie-banner">
            <span class="calorie-value">{{ displayPlan.daily_calories || streamingCalories || '—' }}</span>
            <span class="calorie-unit">千卡 / 日</span>
            <el-tag v-if="displayPlan.version" type="info" size="small">v{{ displayPlan.version }}</el-tag>
            <span v-if="displayPlan.generated_at" class="gen-time">{{ formatTime(displayPlan.generated_at) }}</span>
          </div>
        </div>

        <!-- 饮食 -->
        <div v-if="mealEntries.length" id="plan-diet" class="section-card diet-card">
          <div class="section-head">
            <h3><span class="emoji">🍽️</span> 饮食方案</h3>
            <el-tag type="success" effect="plain">低 GI 优先</el-tag>
          </div>

          <template v-if="isMobile">
            <div class="mobile-meal-list">
              <article v-for="meal in mealEntries" :key="meal.key" class="mobile-meal-card">
                <div class="mobile-meal-card__head">
                  <div>
                    <strong>{{ meal.label }}</strong>
                    <span class="mobile-meal-card__time">{{ meal.time }}</span>
                  </div>
                  <el-tag size="small" type="warning">{{ meal.totalCalories }} kcal</el-tag>
                </div>
                <div class="mobile-food-list">
                  <div v-for="(food, idx) in meal.foods" :key="idx" class="mobile-food-row">
                    <div class="mobile-food-row__main">
                      <span class="mobile-food-row__name">{{ food.name }}</span>
                      <span v-if="food.amount" class="mobile-food-row__amount">{{ food.amount }}</span>
                    </div>
                    <div class="mobile-food-row__meta">
                      <span>{{ food.calories }} kcal</span>
                      <el-tag size="small" :type="giTagType(food.gi_level)">GI {{ giLabel(food.gi_level) }}</el-tag>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </template>
          <el-timeline v-else>
            <el-timeline-item
              v-for="meal in mealEntries"
              :key="meal.key"
              :timestamp="meal.time"
              placement="top"
              :color="meal.color"
            >
              <div class="meal-block">
                <div class="meal-title">
                  {{ meal.label }}
                  <el-tag size="small" type="warning">{{ meal.totalCalories }} kcal</el-tag>
                </div>
                <div class="food-grid">
                  <div v-for="(food, idx) in meal.foods" :key="idx" class="food-item">
                    <div class="food-name">{{ food.name }}</div>
                    <div class="food-meta">
                      <span v-if="food.amount">{{ food.amount }}</span>
                      <span>{{ food.calories }} kcal</span>
                      <el-tag size="small" :type="giTagType(food.gi_level)">GI {{ giLabel(food.gi_level) }}</el-tag>
                    </div>
                  </div>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>

          <div v-if="dietPrinciples.length" class="tips-block">
            <h4>饮食原则</h4>
            <ul>
              <li v-for="(tip, i) in dietPrinciples" :key="i">{{ tip }}</li>
            </ul>
          </div>
          <el-row v-if="foodsRecommend.length || foodsAvoid.length" :gutter="16" class="food-lists">
            <el-col :xs="24" :sm="12" :span="12" v-if="foodsRecommend.length">
              <div class="list-box recommend">
                <h4>推荐食物</h4>
                <el-tag v-for="f in foodsRecommend" :key="f" size="small" type="success">{{ f }}</el-tag>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :span="12" v-if="foodsAvoid.length">
              <div class="list-box avoid">
                <h4>应避免</h4>
                <el-tag v-for="f in foodsAvoid" :key="f" size="small" type="danger">{{ f }}</el-tag>
              </div>
            </el-col>
          </el-row>
        </div>

        <!-- 运动 -->
        <div v-if="exerciseItems.length" id="plan-exercise" class="section-card">
          <div class="section-head">
            <h3><span class="emoji">🏃</span> 运动方案</h3>
            <span v-if="exerciseGoal" class="sub-tip">{{ exerciseGoal }}</span>
          </div>
          <div v-if="isMobile" class="mobile-exercise-list">
            <article v-for="(row, index) in exerciseItems" :key="index" class="mobile-exercise-card">
              <div class="mobile-exercise-card__head">
                <span class="exercise-type">{{ row.type }}</span>
                <el-tag size="small" :type="intensityType(row.intensity)">{{ row.intensity || '—' }}</el-tag>
              </div>
              <div class="mobile-exercise-card__grid">
                <div class="mobile-exercise-kv">
                  <span>时长</span>
                  <strong>{{ row.duration || '—' }}</strong>
                </div>
                <div class="mobile-exercise-kv">
                  <span>频率</span>
                  <strong>{{ row.frequency || '—' }}</strong>
                </div>
                <div class="mobile-exercise-kv">
                  <span>消耗</span>
                  <strong>{{ row.calories_burned ? `${row.calories_burned} kcal` : '—' }}</strong>
                </div>
              </div>
              <p v-if="row.caution" class="mobile-exercise-card__caution">{{ row.caution }}</p>
            </article>
          </div>
          <el-table v-else :data="exerciseItems" stripe size="default" class="exercise-table">
            <el-table-column prop="type" label="类型" min-width="100">
              <template #default="{ row }">
                <span class="exercise-type">{{ row.type }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="时长" width="100" />
            <el-table-column prop="frequency" label="频率" width="110" />
            <el-table-column prop="intensity" label="强度" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="intensityType(row.intensity)">{{ row.intensity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="calories_burned" label="消耗(kcal)" width="110" />
            <el-table-column prop="caution" label="注意事项" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>

        <!-- 作息 -->
        <div v-if="hasRestPlan" id="plan-rest" class="section-card">
          <div class="section-head">
            <h3><span class="emoji">😴</span> 作息方案</h3>
          </div>
          <div v-if="isMobile" class="mobile-rest-grid">
            <div class="mobile-rest-card">
              <span class="mobile-rest-card__label">起床</span>
              <strong>{{ restPlan.wake_up || '—' }}</strong>
            </div>
            <div class="mobile-rest-card">
              <span class="mobile-rest-card__label">就寝</span>
              <strong>{{ restPlan.sleep || '—' }}</strong>
            </div>
            <div class="mobile-rest-card mobile-rest-card--wide">
              <span class="mobile-rest-card__label">午休</span>
              <strong>{{ restPlan.nap || '—' }}</strong>
            </div>
            <div v-if="restPlan.glucose_monitor_times?.length" class="mobile-rest-card mobile-rest-card--wide">
              <span class="mobile-rest-card__label">血糖监测</span>
              <div class="mobile-rest-tags">
                <el-tag
                  v-for="t in restPlan.glucose_monitor_times"
                  :key="t"
                  size="small"
                  class="monitor-tag"
                >{{ t }}</el-tag>
              </div>
            </div>
          </div>
          <el-descriptions v-else :column="2" border class="rest-desc">
            <el-descriptions-item label="起床">{{ restPlan.wake_up || '—' }}</el-descriptions-item>
            <el-descriptions-item label="就寝">{{ restPlan.sleep || '—' }}</el-descriptions-item>
            <el-descriptions-item label="午休" :span="2">{{ restPlan.nap || '—' }}</el-descriptions-item>
            <el-descriptions-item label="血糖监测" :span="2">
              <el-tag
                v-for="t in restPlan.glucose_monitor_times || []"
                :key="t"
                size="small"
                class="monitor-tag"
              >{{ t }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <ul v-if="restTips.length" class="tips-list">
            <li v-for="(tip, i) in restTips" :key="i">{{ tip }}</li>
          </ul>
        </div>

        <!-- 用药 -->
        <div v-if="displayPlan.medication_note" id="plan-med" class="section-card med-card">
          <div class="section-head">
            <h3><span class="emoji">💊</span> 用药提醒</h3>
          </div>
          <el-alert type="warning" :closable="false" show-icon>
            {{ displayPlan.medication_note }}
          </el-alert>
        </div>

        <DisclaimerBar />
      </template>

      <!-- 空状态 -->
      <div v-else-if="!generating" class="section-card empty-card" :class="{ 'empty-card--mobile': isMobile }">
        <el-empty description="暂无健康方案">
          <template #image>
            <div class="empty-icon">📋</div>
          </template>
          <p class="empty-hint">完成风险预测后，可一键生成个性化方案；也可直接点击上方按钮生成</p>
          <el-button type="primary" class="empty-action-btn" @click="handleGenerate">立即生成</el-button>
        </el-empty>
      </div>

      <!-- 历史 -->
      <div v-if="isMobile" id="plan-history" class="section-card history-card history-collapse">
        <button type="button" class="history-collapse__summary" @click="historyOpen = !historyOpen">
          <div>
            <h3>方案历史</h3>
            <span class="sub-tip">共 {{ historyTotal }} 条</span>
          </div>
          <span class="history-collapse__chevron" :class="{ open: historyOpen }">›</span>
        </button>
        <div v-show="historyOpen" class="history-collapse__body">
          <el-timeline v-if="history.length">
            <el-timeline-item
              v-for="p in history"
              :key="p.plan_id"
              :timestamp="formatTime(p.generated_at)"
              :type="p.plan_id === plan?.plan_id ? 'primary' : undefined"
            >
              <button type="button" class="history-item history-item--mobile" @click="loadHistoryPlan(p.plan_id)">
                <span>版本 v{{ p.version }}</span>
                <el-tag size="small">{{ p.daily_calories }} 千卡/日</el-tag>
                <el-tag v-if="isFavoriteFlag(p.is_favorite)" size="small" type="warning">已收藏</el-tag>
              </button>
            </el-timeline-item>
          </el-timeline>
          <p v-else class="muted">暂无历史记录</p>
        </div>
      </div>
      <div v-else id="plan-history" class="section-card history-card">
        <div class="section-head">
          <h3>方案历史</h3>
          <span class="sub-tip">共 {{ historyTotal }} 条</span>
        </div>
        <el-timeline v-if="history.length">
          <el-timeline-item
            v-for="p in history"
            :key="p.plan_id"
            :timestamp="formatTime(p.generated_at)"
            :type="p.plan_id === plan?.plan_id ? 'primary' : undefined"
          >
            <div class="history-item" @click="loadHistoryPlan(p.plan_id)">
              <span>版本 v{{ p.version }}</span>
              <el-tag size="small">{{ p.daily_calories }} 千卡/日</el-tag>
              <el-tag v-if="isFavoriteFlag(p.is_favorite)" size="small" type="warning">已收藏</el-tag>
            </div>
          </el-timeline-item>
        </el-timeline>
        <p v-else class="muted">暂无历史记录</p>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, onMounted, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Star, StarFilled, Printer } from '@element-plus/icons-vue'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import DisclaimerBar from '@/components/DisclaimerBar.vue'
import { getLatestPlan, generatePlan, getPlanHistory, togglePlanFavorite, getPlanDetail } from '@/api/plan'
import { normalizePlan } from '@/utils/normalize'
import { useMessageCenter } from '@/composables/useMessageCenter'
import { useIsMobile } from '@/composables/useBreakpoints'

const isMobile = useIsMobile()
const plan = ref(null)
const previewPlan = ref(null)
const history = ref([])
const historyTotal = ref(0)
const generating = ref(false)
const favoriteLoading = ref(false)
/** 收藏按钮即时状态，避免 plan 对象深层字段未触发视图更新 */
const planFavoriteOverride = ref(null)
const genStep = ref(0)
const genProgress = ref(0)
const streamingCalories = ref(null)
const historyOpen = ref(false)
const summaryExpanded = ref(false)
const activeSection = ref('plan-overview')

const DIM_EMOJI = {
  diet: '🍽️',
  exercise: '🏃',
  rest: '😴',
  med: '💊',
}

const DIM_SECTION_MAP = {
  diet: 'plan-diet',
  exercise: 'plan-exercise',
  rest: 'plan-rest',
  med: 'plan-med',
}

const genStepLabels = [
  { key: 'calorie', title: '热量计算', desc: 'Mifflin-St Jeor' },
  { key: 'diet', title: '饮食方案', desc: '三餐 + 加餐' },
  { key: 'exercise', title: '运动方案', desc: '有氧 + 力量' },
  { key: 'rest', title: '作息方案', desc: '睡眠 + 监测' },
]

const MEAL_META = {
  breakfast: { label: '早餐', color: '#0d9488' },
  lunch: { label: '午餐', color: '#409eff' },
  dinner: { label: '晚餐', color: '#e6a23c' },
  snack: { label: '加餐', color: '#67c23a' },
}

const displayPlan = computed(() => previewPlan.value || plan.value)

watch(
  () => plan.value?.plan_id,
  () => {
    planFavoriteOverride.value = null
    summaryExpanded.value = false
    activeSection.value = 'plan-overview'
  },
)

const summaryNeedsToggle = computed(() => (plan.value?.summary?.length || 0) > 72)

const isPlanFavorited = computed(() => {
  if (planFavoriteOverride.value !== null) return planFavoriteOverride.value
  return isFavoriteFlag(plan.value?.is_favorite ?? plan.value?.isFavorite)
})

function isFavoriteFlag(value) {
  return value === true || value === 1 || value === '1'
}

function syncFavoriteInHistory(planId, favorited) {
  const flag = favorited ? 1 : 0
  history.value = history.value.map((item) =>
    item.plan_id === planId ? { ...item, is_favorite: flag } : item,
  )
}

const dimensions = computed(() => {
  const p = displayPlan.value
  const dietReady = !!p?.diet_plan?.meal_plan
  const exerciseReady = exerciseItems.value.length > 0
  const restReady = hasRestPlan.value
  const medReady = !!p?.medication_note
  return [
    { key: 'diet', label: '饮食', percent: dietReady ? 100 : generating.value ? 40 : 0, color: '#0d9488', ready: dietReady },
    { key: 'exercise', label: '运动', percent: exerciseReady ? 100 : generating.value ? 60 : 0, color: '#409eff', ready: exerciseReady },
    { key: 'rest', label: '作息', percent: restReady ? 100 : generating.value ? 80 : 0, color: '#e6a23c', ready: restReady },
    { key: 'med', label: '用药', percent: medReady ? 100 : 0, color: '#f56c6c', ready: medReady },
  ]
})

const readyDimCount = computed(() => dimensions.value.filter((dim) => dim.ready).length)

const overallPlanPercent = computed(() => {
  if (!dimensions.value.length) return 0
  const total = dimensions.value.reduce((sum, dim) => sum + dim.percent, 0)
  return Math.round(total / dimensions.value.length)
})

const mealEntries = computed(() => {
  const mealPlan = displayPlan.value?.diet_plan?.meal_plan
  if (!mealPlan) return []
  return Object.entries(mealPlan).map(([key, meal]) => ({
    key,
    label: MEAL_META[key]?.label || key,
    color: MEAL_META[key]?.color || '#909399',
    time: meal.time || MEAL_META[key]?.label,
    foods: meal.foods || [],
    totalCalories: meal.total_calories ?? meal.calories ?? 0,
  }))
})

const dietPrinciples = computed(() => displayPlan.value?.diet_plan?.diet_principles || [])
const foodsRecommend = computed(() => displayPlan.value?.diet_plan?.foods_to_recommend || [])
const foodsAvoid = computed(() => displayPlan.value?.diet_plan?.foods_to_avoid || [])

const exerciseItems = computed(() => {
  const ep = displayPlan.value?.exercise_plan
  if (!ep) return []
  return ep.items || (Array.isArray(ep) ? ep : [])
})

const exerciseGoal = computed(() => displayPlan.value?.exercise_plan?.weekly_goal || '')

const restPlan = computed(() => displayPlan.value?.rest_plan || {})
const hasRestPlan = computed(() => !!(restPlan.value.wake_up || restPlan.value.sleep || restPlan.value.glucose_monitor_times?.length))
const restTips = computed(() => restPlan.value.routine_tips || [])

const sectionNavItems = computed(() => {
  const items = [{ id: 'plan-overview', label: '概览', icon: '📊' }]
  if (mealEntries.value.length) items.push({ id: 'plan-diet', label: '饮食', icon: '🍽️' })
  if (exerciseItems.value.length) items.push({ id: 'plan-exercise', label: '运动', icon: '🏃' })
  if (hasRestPlan.value) items.push({ id: 'plan-rest', label: '作息', icon: '😴' })
  if (displayPlan.value?.medication_note) items.push({ id: 'plan-med', label: '用药', icon: '💊' })
  items.push({ id: 'plan-history', label: '历史', icon: '🕘' })
  return items
})

function scrollToSection(id) {
  activeSection.value = id
  if (id === 'plan-history' && isMobile.value) {
    historyOpen.value = true
  }
  requestAnimationFrame(() => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function scrollToDimSection(key) {
  const sectionId = DIM_SECTION_MAP[key]
  if (sectionId && document.getElementById(sectionId)) {
    scrollToSection(sectionId)
    return
  }
  scrollToSection('plan-overview')
}

function formatShortTime(iso) {
  if (!iso) return ''
  try {
    const date = new Date(iso)
    const now = new Date()
    const sameDay = date.toDateString() === now.toDateString()
    if (sameDay) {
      return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
    }
    return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
  } catch {
    return formatTime(iso)
  }
}

onMounted(async () => {
  await loadPlan()
  await loadHistory()
})

async function loadPlan() {
  try {
    plan.value = await getLatestPlan()
  } catch {
    plan.value = null
  }
}

async function loadHistory() {
  try {
    const data = await getPlanHistory()
    history.value = data.list
    historyTotal.value = data.total
  } catch {
    history.value = []
  }
}

async function loadHistoryPlan(planId) {
  try {
    plan.value = await getPlanDetail(planId)
    previewPlan.value = null
    ElMessage.success('已加载历史方案')
  } catch {
    ElMessage.error('加载方案失败')
  }
}

function formatTime(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

function giLabel(level) {
  return { low: '低', medium: '中', high: '高' }[level] || level || '—'
}

function giTagType(level) {
  return { low: 'success', medium: 'warning', high: 'danger' }[level] || 'info'
}

function intensityType(v) {
  if (!v) return 'info'
  if (v.includes('高') || v.includes('强')) return 'danger'
  if (v.includes('中')) return 'warning'
  return 'success'
}

async function handleGenerate() {
  generating.value = true
  genStep.value = 0
  genProgress.value = 0
  ElMessage.info('分析中，请稍后查看')
  streamingCalories.value = null
  previewPlan.value = reactive({
    daily_calories: null,
    diet_plan: {},
    exercise_plan: { items: [] },
    rest_plan: {},
    medication_note: '',
  })

  const stageMap = { calorie: 0, diet: 1, exercise: 2, rest: 3, medication: 3, complete: 4 }

  try {
    await generatePlan({
      onStage: (s) => {
        genStep.value = stageMap[s.stage] ?? genStep.value
        genProgress.value = Math.min(100, genProgress.value + (s.stage === 'complete' ? 25 : 20))

        if (s.stage === 'calorie') {
          streamingCalories.value = s.daily_calories
          previewPlan.value.daily_calories = s.daily_calories
        } else if (s.stage === 'diet') {
          previewPlan.value.diet_plan = normalizePlan({ diet_plan: s.content })?.diet_plan || s.content
        } else if (s.stage === 'exercise') {
          previewPlan.value.exercise_plan = normalizePlan({ exercise_plan: s.content })?.exercise_plan || s.content
        } else if (s.stage === 'rest') {
          previewPlan.value.rest_plan = normalizePlan({ rest_plan: s.content })?.rest_plan || s.content
        } else if (s.stage === 'medication') {
          previewPlan.value.medication_note = typeof s.content === 'string' ? s.content : ''
        } else if (s.stage === 'complete') {
          previewPlan.value = null
          loadPlan()
          loadHistory()
          useMessageCenter().refresh()
        }
      },
    })
  } catch (e) {
    ElMessage.error(e.message || '方案生成失败')
    useMessageCenter().refresh()
    previewPlan.value = null
  } finally {
    generating.value = false
  }
}

async function handleFavorite() {
  if (!plan.value?.plan_id || favoriteLoading.value) return
  favoriteLoading.value = true
  try {
    const { favorited: toggled } = await togglePlanFavorite(plan.value.plan_id, isPlanFavorited.value)
    planFavoriteOverride.value = toggled
    plan.value = normalizePlan({
      ...plan.value,
      is_favorite: toggled ? 1 : 0,
      isFavorite: toggled ? 1 : 0,
    })
    syncFavoriteInHistory(plan.value.plan_id, toggled)
    ElMessage.success(toggled ? '已收藏' : '已取消收藏')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    favoriteLoading.value = false
  }
}

function handlePrint() {
  window.print()
}
</script>

<style scoped>
.plan-page {
  max-width: none;
  margin: 0;
}

.plan-page--mobile {
  padding: 12px 16px calc(80px + env(safe-area-inset-bottom));
  overflow-x: hidden;
  box-sizing: border-box;
}

.hero-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(135deg, #f0fdfa 0%, #ecfdf5 50%, #fff 100%);
  border: 1px solid #99f6e4;
}

.plan-page--mobile .hero-card {
  position: relative;
  overflow: hidden;
  flex-direction: column;
  align-items: stretch;
  border-radius: 20px;
  padding: 20px 16px 16px;
  border: none;
  background: linear-gradient(145deg, #ecfdf5 0%, #f0fdfa 42%, #ffffff 100%);
  box-shadow: 0 10px 32px rgba(13, 148, 136, 0.14);
}

.hero-card__decor {
  position: absolute;
  top: -36px;
  right: -24px;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(45, 212, 191, 0.38) 0%, rgba(45, 212, 191, 0) 72%);
  pointer-events: none;
}

.hero-card__decor::after {
  content: '';
  position: absolute;
  bottom: -58px;
  left: -96px;
  width: 108px;
  height: 108px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(20, 184, 166, 0.16) 0%, rgba(20, 184, 166, 0) 72%);
}

.hero-eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--health-600);
}

.hero-text h2 {
  margin: 0 0 10px;
  font-size: 20px;
  color: #134e4a;
}

.plan-page--mobile .hero-text {
  position: relative;
  z-index: 1;
}

.plan-page--mobile .hero-text h2 {
  font-size: 24px;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.hero-text p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  max-width: 520px;
  line-height: 1.6;
}

.hero-summary-wrap {
  margin-bottom: 4px;
}

.hero-summary {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.75;
}

.hero-summary--collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hero-summary-toggle {
  margin-top: 8px;
  padding: 0;
  border: none;
  background: none;
  color: var(--health-600);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.hero-stats-row {
  display: flex;
  align-items: stretch;
  gap: 10px;
  margin-top: 16px;
}

.hero-calorie-pill {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(153, 246, 228, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.hero-calorie-pill__label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.hero-calorie-pill__main {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.hero-calorie-pill__value {
  font-size: 26px;
  font-weight: 800;
  color: var(--health-600);
  line-height: 1;
  letter-spacing: -0.03em;
}

.hero-calorie-pill__unit {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.hero-meta-stack {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-width: 72px;
}

.hero-meta-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid #ccfbf1;
  font-size: 12px;
  font-weight: 700;
  color: var(--health-700);
}

.hero-meta-time {
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
  line-height: 1.3;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-actions--mobile {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  margin-top: 16px;
}

.hero-actions--mobile .hero-generate-btn {
  width: 100%;
  height: 48px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 8px 20px rgba(13, 148, 136, 0.28);
}

.hero-secondary-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.hero-secondary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 44px;
  border-radius: 14px;
  border: 1px solid rgba(231, 229, 228, 0.95);
  background: rgba(255, 255, 255, 0.92);
  color: #57534e;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.hero-secondary-btn .el-icon {
  font-size: 16px;
}

.hero-secondary-btn:active {
  transform: scale(0.98);
}

.hero-secondary-btn--active {
  border-color: #fcd34d;
  background: #fffbeb;
  color: #b45309;
}

.hero-secondary-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.mobile-section-nav {
  margin: 0 0 12px;
  position: sticky;
  top: calc(var(--header-height) - 1px);
  z-index: 20;
  padding: 2px 0 6px;
  background: linear-gradient(180deg, var(--warm-50) 78%, rgba(250, 250, 249, 0));
}

.mobile-section-nav__track {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 8px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 6px 24px rgba(15, 23, 42, 0.08);
  scrollbar-width: none;
}

.mobile-section-nav__track::-webkit-scrollbar {
  display: none;
}

.mobile-section-nav__chip {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: #f5f5f4;
  color: #78716c;
  padding: 11px 16px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: transform 0.15s ease, background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.mobile-section-nav__icon {
  font-size: 15px;
  line-height: 1;
}

.mobile-section-nav__chip.active {
  background: linear-gradient(135deg, var(--health-500), var(--health-600));
  color: #fff;
  box-shadow: 0 6px 16px rgba(13, 148, 136, 0.28);
}

.mobile-section-nav__chip:active {
  transform: scale(0.96);
}

.progress-card {
  text-align: center;
}

.progress-card .el-progress {
  margin-top: 20px;
}

.mobile-progress-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #134e4a;
}

.mobile-gen-steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 16px 0 4px;
  text-align: left;
}

.mobile-gen-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  opacity: 0.55;
  transition: opacity 0.2s ease, background 0.2s ease;
}

.mobile-gen-step.active,
.mobile-gen-step.done {
  opacity: 1;
}

.mobile-gen-step.active {
  background: #ecfdf5;
  border: 1px solid #99f6e4;
}

.mobile-gen-step__dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: #e2e8f0;
  color: #64748b;
  flex-shrink: 0;
}

.mobile-gen-step.active .mobile-gen-step__dot,
.mobile-gen-step.done .mobile-gen-step__dot {
  background: var(--health-600);
  color: #fff;
}

.mobile-gen-step__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mobile-gen-step__text strong {
  font-size: 14px;
  color: #134e4a;
}

.mobile-gen-step__text span {
  font-size: 12px;
  color: #94a3b8;
}

.stream-tip {
  margin-top: 12px;
  color: #0d9488;
  font-size: 15px;
}

.overview-card {
  text-align: center;
}

.plan-page--mobile .overview-card {
  text-align: left;
  border-radius: 20px;
  padding: 18px 16px;
}

.mobile-overview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.mobile-overview__head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #134e4a;
}

.mobile-overview__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: var(--health-700);
  font-size: 13px;
  font-weight: 700;
}

.mobile-overview__track {
  height: 8px;
  border-radius: 999px;
  background: #e7e5e4;
  overflow: hidden;
  margin-bottom: 14px;
}

.mobile-overview__fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--health-400), var(--health-600));
  transition: width 0.35s ease;
}

.mobile-dim-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.mobile-dim-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #e7e5e4;
  background: #fafaf9;
  text-align: left;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.mobile-dim-card:active {
  transform: scale(0.98);
}

.mobile-dim-card.ready {
  border-color: #99f6e4;
  background: linear-gradient(180deg, #ffffff 0%, #f0fdfa 100%);
  box-shadow: 0 4px 16px rgba(13, 148, 136, 0.08);
}

.mobile-dim-card__emoji {
  font-size: 22px;
  line-height: 1;
}

.mobile-dim-card__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
}

.mobile-dim-card__body strong {
  font-size: 15px;
  color: #134e4a;
}

.mobile-dim-card__status {
  font-size: 11px;
  color: #94a3b8;
}

.mobile-dim-card.ready .mobile-dim-card__status {
  color: var(--health-600);
  font-weight: 600;
}

.mobile-dim-card__bar {
  width: 100%;
  height: 4px;
  border-radius: 999px;
  background: #e7e5e4;
  overflow: hidden;
}

.mobile-dim-card__bar-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.35s ease;
}

.dim-card {
  padding: 12px 0;
  opacity: 0.55;
  transition: opacity 0.3s;
}

.dim-card.active {
  opacity: 1;
}

.dim-label {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}

.calorie-banner {
  margin-top: 20px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 12px;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.plan-page--mobile .overview-card .calorie-banner {
  margin-top: 14px;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f0fdfa 0%, #ecfdf5 100%);
  border: 1px solid #99f6e4;
}

.calorie-value {
  font-size: 36px;
  font-weight: 700;
  color: #0d9488;
  line-height: 1;
}

.plan-page--mobile .calorie-value {
  font-size: 32px;
}

.calorie-unit {
  font-size: 14px;
  color: #64748b;
}

.gen-time {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 8px;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.section-head h3 {
  margin: 0;
  font-size: 17px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.plan-page--mobile .section-head h3 {
  font-size: 16px;
}

.sub-tip {
  font-size: 13px;
  color: #909399;
}

.mobile-meal-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mobile-meal-card {
  background: #fafafa;
  border-radius: 14px;
  padding: 14px;
  border: 1px solid #f1f5f9;
}

.mobile-meal-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.mobile-meal-card__head strong {
  display: block;
  font-size: 15px;
  color: #134e4a;
}

.mobile-meal-card__time {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}

.mobile-food-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mobile-food-row {
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
}

.mobile-food-row__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.mobile-food-row__name {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.mobile-food-row__amount {
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}

.mobile-food-row__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.meal-block {
  background: #fafafa;
  border-radius: 10px;
  padding: 12px 14px;
}

.meal-title {
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.food-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.food-item {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
}

.food-name {
  font-weight: 500;
  margin-bottom: 6px;
}

.food-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  align-items: center;
}

.tips-block {
  margin-top: 16px;
  padding: 12px 16px;
  background: #f0fdf4;
  border-radius: 8px;
}

.tips-block h4,
.list-box h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.tips-block ul,
.tips-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
}

.food-lists {
  margin-top: 16px;
}

.list-box {
  padding: 12px;
  border-radius: 8px;
}

.list-box.recommend {
  background: #f0fdf4;
}

.list-box.avoid {
  background: #fef2f2;
}

.list-box .el-tag {
  margin: 0 6px 6px 0;
}

.mobile-exercise-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mobile-exercise-card {
  background: #fafafa;
  border-radius: 14px;
  padding: 14px;
  border: 1px solid #f1f5f9;
}

.mobile-exercise-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.mobile-exercise-card__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.mobile-exercise-kv {
  background: #fff;
  border-radius: 10px;
  padding: 10px 8px;
  text-align: center;
  border: 1px solid #ebeef5;
}

.mobile-exercise-kv span {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.mobile-exercise-kv strong {
  font-size: 13px;
  color: #334155;
  font-weight: 600;
}

.mobile-exercise-card__caution {
  margin: 10px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  line-height: 1.6;
}

.exercise-type {
  font-weight: 500;
}

.mobile-rest-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.mobile-rest-card {
  background: #fafafa;
  border-radius: 14px;
  padding: 14px;
  border: 1px solid #f1f5f9;
}

.mobile-rest-card--wide {
  grid-column: 1 / -1;
}

.mobile-rest-card__label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.mobile-rest-card strong {
  font-size: 18px;
  color: #134e4a;
  font-weight: 700;
}

.mobile-rest-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.monitor-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.med-card :deep(.el-alert) {
  border-radius: 8px;
}

.empty-card {
  text-align: center;
  padding: 40px 20px;
}

.empty-card--mobile {
  padding: 32px 16px;
  border-radius: 18px;
}

.empty-icon {
  font-size: 48px;
  line-height: 1;
}

.empty-hint {
  color: #909399;
  font-size: 14px;
  margin: 8px 0 16px;
  line-height: 1.6;
}

.empty-action-btn {
  width: 100%;
  max-width: 240px;
  height: 44px;
  border-radius: 12px;
}

.history-collapse {
  padding: 0;
  overflow: hidden;
}

.history-collapse__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  width: 100%;
  border: none;
  background: none;
  text-align: left;
  font: inherit;
  color: inherit;
}

.history-collapse__summary::-webkit-details-marker {
  display: none;
}

.history-collapse__summary h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.history-collapse__chevron {
  font-size: 22px;
  color: #94a3b8;
  transform: rotate(0deg);
  transition: transform 0.2s ease;
}

.history-collapse__chevron.open {
  transform: rotate(90deg);
}

.history-collapse__body :deep(.el-timeline),
.history-collapse__body .muted {
  padding: 0 16px 16px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 0;
  border: none;
  background: none;
  width: 100%;
  text-align: left;
  font: inherit;
  color: inherit;
}

.history-item--mobile {
  padding: 8px 0;
}

.history-item:hover,
.history-item:active {
  color: #0d9488;
}

.muted {
  color: #909399;
  font-size: 13px;
}

@media print {
  .hero-actions,
  .history-card,
  .progress-card,
  .mobile-section-nav {
    display: none !important;
  }
}

@media (max-width: 768px) {
  .hero-card {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions:not(.hero-actions--mobile) {
    width: 100%;
  }

  .hero-actions:not(.hero-actions--mobile) .el-button {
    flex: 1;
    min-width: 0;
  }

  .progress-card :deep(.el-steps) {
    overflow-x: auto;
  }

  .food-lists {
    row-gap: 12px;
  }

  .food-grid {
    grid-template-columns: 1fr;
  }

  .exercise-table {
    width: 100%;
  }
}
</style>
