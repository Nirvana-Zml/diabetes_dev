<template>
  <SiteLayout title="打卡分析">

    <div class="page-container">
      <!-- 时间筛选 -->
      <div class="section-card filter-bar">
        <el-radio-group v-model="period" @change="loadData">
          <el-radio-button value="weekly">周报</el-radio-button>
          <el-radio-button value="monthly">月报</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="period === 'custom'"
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          style="margin-top:10px;width:100%"
          @change="loadData"
        />
      </div>

      <!-- 统计概览 -->
      <el-row :gutter="12" class="stat-row">
        <el-col :span="6" v-for="s in statCards" :key="s.label">
          <div class="section-card stat-card">
            <div class="stat-num">{{ s.value }}</div>
            <div class="stat-lbl">{{ s.label }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 趋势图 -->
      <div class="section-card">
        <h3 class="section-title">打卡趋势</h3>
        <el-tabs v-model="chartType">
          <el-tab-pane label="饮食" name="diet" />
          <el-tab-pane label="运动" name="exercise" />
          <el-tab-pane label="用药" name="medication" />
          <el-tab-pane label="血糖" name="glucose" />
        </el-tabs>
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

      <div class="section-card">
        <el-button type="primary" @click="handleExport">导出统计报告</el-button>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { getManagementStats, getManagementTrends, getAiSummary, exportReport } from '@/api/checkinManagement'
import { buildDateRange } from '@/utils/normalize'

const AI_CACHE_KEY = 'checkin_ai_summary_cache'

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

onMounted(loadData)
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
  try {
    const ai = await getAiSummary(params)
    applyAiData(ai)
    writeAiCache(key, ai)
    aiCacheStale.value = false
  } catch (e) {
    if (!hasAiContent.value) {
      ElMessage.warning(e.message || 'AI 分析加载失败')
    }
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
    xAxis: { type: 'category', data: data.map((d) => d.date) },
    yAxis: { type: 'value' },
    series: [{
      type: isGlucose ? 'line' : 'bar',
      data: data.map((d) => d.value ?? d.count),
      itemStyle: { color: '#0d9488' },
      smooth: true,
    }],
  })
}

async function handleExport() {
  const res = await exportReport({ period: period.value })
  ElMessage.success('导出任务已提交（占位 URL: ' + res.export_url + '）')
}
</script>

<style scoped>
.filter-bar { text-align: center; }
.stat-row { margin-bottom: 0; }
.stat-card { text-align: center; padding: 12px !important; }
.stat-num { font-size: 20px; font-weight: 700; color: #0d9488; }
.stat-lbl { font-size: 11px; color: #909399; margin-top: 4px; }
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
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}
.pattern-header { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pattern-rate { font-size: 12px; color: #606266; margin-left: auto; }
.pattern-desc { font-size: 13px; color: #606266; margin: 8px 0 4px; }
.pattern-suggestion { font-size: 12px; color: #0d9488; margin: 0; }
.anomaly-item { margin-bottom: 8px; }
.anomaly-reason { font-size: 12px; color: #909399; margin: 4px 0 0; }
.improvement-list { margin: 0; padding-left: 20px; color: #606266; font-size: 13px; }
.improvement-list li { margin-bottom: 6px; }
</style>
