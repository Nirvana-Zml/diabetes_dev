<template>
  <SiteLayout title="">

    <div class="page-container plan-page">
      <!-- 顶部操作区 -->
      <div class="section-card hero-card">
        <div class="hero-text">
          <h2>个性化健康管理方案</h2>
          <p v-if="plan?.summary">{{ plan.summary }}</p>
          <p v-else class="muted">结合健康档案、风险评估与打卡数据，AI 为您定制饮食、运动与作息计划</p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" size="large" :loading="generating" @click="handleGenerate">
            <el-icon v-if="!generating"><MagicStick /></el-icon>
            {{ plan ? '重新生成方案' : '生成健康方案' }}
          </el-button>
          <template v-if="plan">
            <el-button
              size="large"
              :type="isPlanFavorited ? 'warning' : 'default'"
              :plain="isPlanFavorited"
              :loading="favoriteLoading"
              @click="handleFavorite"
            >
              <el-icon><component :is="isPlanFavorited ? StarFilled : Star" /></el-icon>
              {{ isPlanFavorited ? '取消收藏' : '收藏' }}
            </el-button>
            <el-button size="large" @click="handlePrint">
              <el-icon><Printer /></el-icon>
              导出
            </el-button>
          </template>
        </div>
      </div>

      <!-- 生成进度 -->
      <div v-if="generating" class="section-card progress-card">
        <el-steps :active="genStep" finish-status="success" align-center>
          <el-step title="热量计算" description="Mifflin-St Jeor" />
          <el-step title="饮食方案" description="三餐+加餐" />
          <el-step title="运动方案" description="有氧+力量" />
          <el-step title="作息方案" description="睡眠+监测" />
        </el-steps>
        <el-progress :percentage="genProgress" :stroke-width="10" striped striped-flow />
        <p v-if="streamingCalories" class="stream-tip">
          每日推荐摄入：<strong>{{ streamingCalories }}</strong> 千卡
        </p>
      </div>

      <!-- 方案内容 -->
      <template v-if="displayPlan">
        <!-- 概览 -->
        <div class="section-card overview-card">
          <el-row :gutter="20">
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
          <div class="calorie-banner">
            <span class="calorie-value">{{ displayPlan.daily_calories || streamingCalories || '—' }}</span>
            <span class="calorie-unit">千卡 / 日</span>
            <el-tag v-if="displayPlan.version" type="info" size="small">v{{ displayPlan.version }}</el-tag>
            <span v-if="displayPlan.generated_at" class="gen-time">{{ formatTime(displayPlan.generated_at) }}</span>
          </div>
        </div>

        <!-- 饮食 -->
        <div v-if="mealEntries.length" class="section-card diet-card">
          <div class="section-head">
            <h3><span class="emoji">🍽️</span> 饮食方案</h3>
            <el-tag type="success" effect="plain">低 GI 优先</el-tag>
          </div>

          <el-timeline>
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
            <el-col :span="12" v-if="foodsRecommend.length">
              <div class="list-box recommend">
                <h4>推荐食物</h4>
                <el-tag v-for="f in foodsRecommend" :key="f" size="small" type="success">{{ f }}</el-tag>
              </div>
            </el-col>
            <el-col :span="12" v-if="foodsAvoid.length">
              <div class="list-box avoid">
                <h4>应避免</h4>
                <el-tag v-for="f in foodsAvoid" :key="f" size="small" type="danger">{{ f }}</el-tag>
              </div>
            </el-col>
          </el-row>
        </div>

        <!-- 运动 -->
        <div v-if="exerciseItems.length" class="section-card">
          <div class="section-head">
            <h3><span class="emoji">🏃</span> 运动方案</h3>
            <span v-if="exerciseGoal" class="sub-tip">{{ exerciseGoal }}</span>
          </div>
          <el-table :data="exerciseItems" stripe size="default" class="exercise-table">
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
        <div v-if="hasRestPlan" class="section-card">
          <div class="section-head">
            <h3><span class="emoji">😴</span> 作息方案</h3>
          </div>
          <el-descriptions :column="2" border class="rest-desc">
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
        <div v-if="displayPlan.medication_note" class="section-card med-card">
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
      <div v-else-if="!generating" class="section-card empty-card">
        <el-empty description="暂无健康方案">
          <template #image>
            <div class="empty-icon">📋</div>
          </template>
          <p class="empty-hint">完成风险预测后，可一键生成个性化方案；也可直接点击上方按钮生成</p>
          <el-button type="primary" @click="handleGenerate">立即生成</el-button>
        </el-empty>
      </div>

      <!-- 历史 -->
      <div class="section-card history-card">
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

const MEAL_META = {
  breakfast: { label: '早餐', color: '#0d9488' },
  lunch: { label: '午餐', color: '#409eff' },
  dinner: { label: '晚餐', color: '#e6a23c' },
  snack: { label: '加餐', color: '#67c23a' },
}

const displayPlan = computed(() => previewPlan.value || plan.value)

watch(
  () => plan.value?.plan_id,
  () => { planFavoriteOverride.value = null },
)

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
          ElMessage.success('方案生成完成')
        }
      },
    })
  } catch (e) {
    ElMessage.error(e.message || '方案生成失败')
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

.hero-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(135deg, #f0fdfa 0%, #ecfdf5 50%, #fff 100%);
  border: 1px solid #99f6e4;
}

.hero-text h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #134e4a;
}

.hero-text p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  max-width: 520px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.progress-card {
  text-align: center;
}

.progress-card .el-progress {
  margin-top: 20px;
}

.stream-tip {
  margin-top: 12px;
  color: #0d9488;
  font-size: 15px;
}

.overview-card {
  text-align: center;
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

.calorie-value {
  font-size: 36px;
  font-weight: 700;
  color: #0d9488;
  line-height: 1;
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
}

.section-head h3 {
  margin: 0;
  font-size: 17px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.sub-tip {
  font-size: 13px;
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

.exercise-type {
  font-weight: 500;
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

.empty-icon {
  font-size: 48px;
  line-height: 1;
}

.empty-hint {
  color: #909399;
  font-size: 14px;
  margin: 8px 0 16px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 0;
}

.history-item:hover {
  color: #0d9488;
}

.muted {
  color: #909399;
  font-size: 13px;
}

@media print {
  .hero-actions,
  .history-card,
  .progress-card {
    display: none !important;
  }
}
</style>
