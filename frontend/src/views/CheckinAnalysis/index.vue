<template>
  <SiteLayout title="打卡分析" show-back>

    <div class="analysis-page page-container">
      <!-- 时间筛选 -->
      <div class="section-card filter-bar">
        <div class="segment-control">
          <button
            v-for="opt in periodOptions"
            :key="opt.value"
            type="button"
            class="segment-btn"
            :class="{ active: period === opt.value }"
            @click="setPeriod(opt.value)"
          >{{ opt.label }}</button>
        </div>
        <el-date-picker
          v-if="period === 'custom'"
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          class="custom-range-picker"
          @change="loadData"
        />
      </div>

      <!-- 统计概览 -->
      <div class="stats-strip">
        <div v-for="s in statCards" :key="s.label" class="stat-card">
          <div class="stat-num">{{ s.value }}</div>
          <div class="stat-lbl">{{ s.label }}</div>
        </div>
      </div>

      <!-- 趋势图 -->
      <div class="section-card chart-panel">
        <h3 class="section-title">打卡趋势</h3>
        <div class="chart-type-nav">
          <button
            v-for="opt in chartTypeOptions"
            :key="opt.value"
            type="button"
            class="chart-type-btn"
            :class="{ active: chartType === opt.value }"
            @click="chartType = opt.value"
          >{{ opt.label }}</button>
        </div>
        <div ref="chartRef" class="chart-box" />
      </div>

      <!-- AI 行为分析 -->
      <div class="section-card ai-panel">
        <div class="ai-panel-head">
          <h3 class="section-title">AI 行为分析</h3>
          <span v-if="aiRefreshing" class="ai-refresh-badge">
            <span class="ai-refresh-dot" />
            分析生成中
          </span>
        </div>

        <el-alert
          v-if="aiRefreshing"
          type="info"
          :closable="false"
          show-icon
          class="ai-refresh-tip"
        >
          {{ aiRefreshTip }}
        </el-alert>

        <el-skeleton :loading="aiInitialLoading && !hasAiContent" animated :rows="4">
          <el-alert v-if="aiSource === 'local' && !aiRefreshing" type="warning" :closable="false" show-icon class="ai-fallback-tip">
            AI 分析暂不可用，已展示本地简要总结
          </el-alert>
          <el-alert type="info" :closable="false" class="ai-summary">
            <MarkdownContent :content="aiSummary || '暂无分析内容'" />
          </el-alert>

          <div v-if="behaviorPatterns.length" class="ai-section">
            <h4 class="ai-subtitle">行为模式</h4>
            <div v-for="(p, i) in behaviorPatterns" :key="i" class="pattern-card">
              <div class="pattern-header">
                <el-tag size="small" type="success">{{ typeLabel(p.type) }}</el-tag>
                <el-tag size="small" :type="patternTagType(p.pattern)">{{ p.pattern }}</el-tag>
                <span v-if="p.completion_rate != null" class="pattern-rate">
                  完成率 {{ Math.round(p.completion_rate * 100) }}%
                </span>
              </div>
              <p class="pattern-desc">{{ p.description }}</p>
              <p v-if="p.suggestion" class="pattern-suggestion">建议：{{ p.suggestion }}</p>
            </div>
          </div>

          <div v-if="anomalies.length" class="ai-section">
            <h4 class="ai-subtitle">异常提醒</h4>
            <el-alert
              v-for="(a, i) in anomalies"
              :key="i"
              type="warning"
              :closable="false"
              show-icon
              class="anomaly-item"
            >
              <template #title>{{ a.date }} · {{ anomalyLabel(a.type) }}</template>
              <p>{{ a.description }}</p>
              <p v-if="a.possible_reason" class="anomaly-reason">可能原因：{{ a.possible_reason }}</p>
            </el-alert>
          </div>

          <div v-if="improvements.length" class="ai-section">
            <h4 class="ai-subtitle">改善建议</h4>
            <ul class="improvement-list">
              <li v-for="(item, i) in improvements" :key="i">{{ item }}</li>
            </ul>
          </div>
        </el-skeleton>
      </div>

      <div class="section-card action-bar">
        <el-button type="primary" class="action-btn" @click="handleExport">导出统计报告</el-button>
        <el-button plain class="action-btn" @click="$router.push('/checkin-reminder-settings')">开启打卡提醒</el-button>
        <el-button plain class="action-btn action-btn--ghost" @click="$router.push('/checkin-records')">返回生活打卡</el-button>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { getManagementStats, getManagementTrends, getAiSummary, exportReport } from '@/api/checkinManagement'
import { buildDateRange } from '@/utils/normalize'
import { useMessageCenter } from '@/composables/useMessageCenter'

const AI_CACHE_KEY = 'checkin_ai_summary_cache'

const periodOptions = [
  { value: 'weekly', label: '周报' },
  { value: 'monthly', label: '月报' },
  { value: 'custom', label: '自定义' },
]

const chartTypeOptions = [
  { value: 'diet', label: '饮食' },
  { value: 'exercise', label: '运动' },
  { value: 'medication', label: '用药' },
  { value: 'glucose', label: '血糖' },
]

const period = ref('weekly')
const dateRange = ref([])
const stats = ref({})
const trends = ref({})
const aiSummary = ref('')
const aiSource = ref('dify')
const behaviorPatterns = ref([])
const anomalies = ref([])
const improvements = ref([])
const aiRefreshing = ref(false)
const aiInitialLoading = ref(true)
const aiCacheStale = ref(false)
const chartType = ref('diet')
const chartRef = ref()
let chart = null

const hasAiContent = computed(() =>
  !!aiSummary.value
  || behaviorPatterns.value.length > 0
  || anomalies.value.length > 0
  || improvements.value.length > 0,
)

const aiRefreshTip = computed(() => {
  if (hasAiContent.value) {
    return aiCacheStale.value
      ? '新的分析正在生成，请稍候… 当前展示的是上次查看的分析结果'
      : '新的分析正在生成，请稍候… 当前展示的是最近一次分析结果'
  }
  return '正在生成 AI 行为分析，请稍候…'
})

const statCards = ref([
  { label: '总打卡', value: '-' },
  { label: '完成率', value: '-' },
  { label: '连续天', value: '-' },
  { label: '积分', value: '-' },
])

const TYPE_LABELS = { diet: '饮食', exercise: '运动', medication: '用药', glucose: '血糖' }
const ANOMALY_LABELS = {
  missed_all: '全部漏打卡',
  glucose_abnormal: '血糖异常',
  medication_missed: '漏服药物',
}

function setPeriod(value) {
  period.value = value
  loadData()
}

function typeLabel(type) {
  return TYPE_LABELS[type] || type
}

function anomalyLabel(type) {
  return ANOMALY_LABELS[type] || type
}

function patternTagType(pattern) {
  if (pattern === '规律') return 'success'
  if (pattern === '不稳定') return 'warning'
  return 'info'
}

function paramsKey(params) {
  const { startDate, endDate } = buildDateRange(params)
  return `${params.period || 'weekly'}_${startDate}_${endDate}`
}

function applyAiData(ai) {
  aiSummary.value = ai.ai_summary
  aiSource.value = ai.source || 'dify'
  behaviorPatterns.value = ai.behavior_patterns || []
  anomalies.value = ai.anomalies || []
  improvements.value = ai.improvements || []
}

function readAiCache() {
  try {
    const raw = sessionStorage.getItem(AI_CACHE_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function writeAiCache(cacheKey, data) {
  try {
    sessionStorage.setItem(AI_CACHE_KEY, JSON.stringify({
      cacheKey,
      data,
      cachedAt: Date.now(),
    }))
  } catch {
    /* ignore quota errors */
  }
}

function restoreAiCache(currentKey) {
  const cached = readAiCache()
  if (!cached?.data) return false
  applyAiData(cached.data)
  aiCacheStale.value = cached.cacheKey !== currentKey
  return true
}

function resizeChart() {
  chart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})

watch(chartType, () => renderChart())

async function loadData() {
  const params = { period: period.value, date_range: dateRange.value }
  const key = paramsKey(params)

  restoreAiCache(key)
  aiRefreshing.value = true
  if (!hasAiContent.value) {
    aiInitialLoading.value = true
  }

  try {
    const [st, tr] = await Promise.all([
      getManagementStats(params),
      getManagementTrends(params),
    ])
    stats.value = st
    trends.value = tr
    statCards.value = [
      { label: '总打卡', value: st.total_checkins },
      { label: '完成率', value: Math.round(st.completion_rate * 100) + '%' },
      { label: '连续天', value: st.streak_days },
      { label: '积分', value: st.total_points },
    ]
    await nextTick()
    renderChart()
  } catch (e) {
    ElMessage.error(e.message || '统计数据加载失败')
  }

  loadAiSummary(params, key)
}

async function loadAiSummary(params, key) {
  ElMessage.info('分析中，请稍后查看')
  try {
    const ai = await getAiSummary(params)
    applyAiData(ai)
    writeAiCache(key, ai)
    aiCacheStale.value = false
    if ((ai.source || ai.ai_source) === 'dify') {
      useMessageCenter().refresh()
    }
  } catch (e) {
    if (!hasAiContent.value) {
      ElMessage.warning(e.message || 'AI 分析加载失败')
    }
    useMessageCenter().refresh()
  } finally {
    aiRefreshing.value = false
    aiInitialLoading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const data = trends.value[chartType.value] || []
  const isGlucose = chartType.value === 'glucose'
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 12, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.date),
      axisLabel: { fontSize: 11, rotate: data.length > 7 ? 35 : 0 },
    },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
    series: [{
      type: isGlucose ? 'line' : 'bar',
      data: data.map((d) => d.value ?? d.count),
      itemStyle: { color: '#0d9488' },
      smooth: true,
      barMaxWidth: 28,
    }],
  })
  resizeChart()
}

async function handleExport() {
  const res = await exportReport({ period: period.value })
  ElMessage.success('导出任务已提交（占位 URL: ' + res.export_url + '）')
}
</script>

<style scoped>
.analysis-page {
  padding-bottom: 8px;
}

.filter-bar {
  text-align: center;
}

.segment-control {
  display: inline-flex;
  width: 100%;
  max-width: 360px;
  padding: 4px;
  background: #f5f5f4;
  border-radius: 12px;
  gap: 4px;
}

.segment-btn {
  flex: 1;
  border: none;
  background: transparent;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #78716c;
  cursor: pointer;
  transition: all 0.2s;
}

.segment-btn.active {
  background: #fff;
  color: var(--health-700);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.custom-range-picker {
  margin-top: 12px;
  width: 100%;
}

.stats-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.stat-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px 10px;
  text-align: center;
  border: 1px solid rgba(231, 229, 228, 0.8);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #0d9488;
  line-height: 1.2;
}

.stat-lbl {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.chart-panel .section-title {
  margin-bottom: 12px;
}

.chart-type-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.chart-type-btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid #e7e5e4;
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  color: #78716c;
  cursor: pointer;
  transition: all 0.2s;
}

.chart-type-btn.active {
  background: var(--health-600);
  border-color: var(--health-600);
  color: #fff;
}

.ai-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.ai-panel-head .section-title { margin: 0; }

.ai-refresh-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #0d9488;
  background: #f0fdfa;
  padding: 4px 10px;
  border-radius: 999px;
  flex-shrink: 0;
}

.ai-refresh-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #0d9488;
  animation: aiPulse 1.2s ease-in-out infinite;
}

@keyframes aiPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.85); }
}

.ai-refresh-tip { margin-bottom: 12px; }
.ai-summary { margin-bottom: 16px; }
.ai-fallback-tip { margin-bottom: 12px; }
.ai-section { margin-top: 16px; }
.ai-subtitle { font-size: 14px; font-weight: 600; color: #303133; margin: 0 0 10px; }

.pattern-card {
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 8px;
}

.pattern-header { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pattern-rate { font-size: 12px; color: #606266; margin-left: auto; }
.pattern-desc { font-size: 13px; color: #606266; margin: 8px 0 4px; line-height: 1.5; }
.pattern-suggestion { font-size: 12px; color: #0d9488; margin: 0; line-height: 1.5; }
.anomaly-item { margin-bottom: 8px; }
.anomaly-reason { font-size: 12px; color: #909399; margin: 4px 0 0; }
.improvement-list { margin: 0; padding-left: 20px; color: #606266; font-size: 13px; line-height: 1.6; }
.improvement-list li { margin-bottom: 6px; }

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.action-btn {
  flex: 1;
  min-width: 140px;
}

.action-btn--ghost {
  display: none;
}

@media (max-width: 768px) {
  .analysis-page {
    padding-bottom: 12px;
  }

  .segment-control {
    max-width: none;
  }

  .segment-btn {
    padding: 10px 8px;
    font-size: 13px;
  }

  .stats-strip {
    display: flex;
    flex-wrap: nowrap;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scroll-snap-type: x proximity;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
    margin: 0 -16px 12px;
    padding: 0 16px 4px;
    gap: 8px;
  }

  .stats-strip::-webkit-scrollbar {
    display: none;
  }

  .stat-card {
    flex: 0 0 88px;
    scroll-snap-align: start;
    padding: 12px 8px;
    border-radius: 12px;
  }

  .stat-num {
    font-size: 18px;
  }

  .stat-lbl {
    font-size: 10px;
  }

  .chart-type-nav {
    flex-wrap: nowrap;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
    margin: 0 -16px 12px;
    padding: 0 16px 2px;
  }

  .chart-type-nav::-webkit-scrollbar {
    display: none;
  }

  .chart-type-btn {
    flex: 0 0 auto;
    white-space: nowrap;
  }

  .chart-panel :deep(.chart-box) {
    height: 200px;
  }

  .ai-panel-head {
    flex-wrap: wrap;
  }

  .pattern-header .pattern-rate {
    margin-left: 0;
    width: 100%;
  }

  .action-bar {
    flex-direction: column;
    gap: 8px;
    margin-bottom: 0;
  }

  .action-btn {
    width: 100%;
    min-width: 0;
    margin: 0;
  }

  .action-btn--ghost {
    display: inline-flex;
  }
}
</style>
