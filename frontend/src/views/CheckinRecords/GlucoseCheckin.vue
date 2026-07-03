<template>
  <CheckinTypeLayout title="血糖打卡" accent="red">
    <template #date>
      <CheckinDateBar />
    </template>

    <div class="glucose-input-card">
      <h3>记录血糖</h3>
      <el-form label-position="top" class="compact-form">
        <el-form-item label="血糖值 (mmol/L)" required>
          <el-input-number v-model="glucoseForm.value" :min="2" :max="30" :step="0.1" style="width:100%" />
        </el-form-item>
        <el-form-item label="测量时段">
          <el-select v-model="glucoseForm.context" style="width:100%">
            <el-option label="空腹" :value="1" />
            <el-option label="餐后2h" :value="2" />
            <el-option label="睡前" :value="3" />
            <el-option label="随机" :value="4" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-button type="primary" round class="submit-btn" :loading="submitting" @click="submitGlucose">
        保存记录
      </el-button>
    </div>

    <div class="glucose-trend-section">
      <div class="trend-head">
        <h3>血糖趋势</h3>
        <el-radio-group v-model="glucoseTrendDays" size="small">
          <el-radio-button :value="7">7天</el-radio-button>
          <el-radio-button :value="14">14天</el-radio-button>
          <el-radio-button :value="30">30天</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="glucoseSummary.count" class="glucose-stats">
        <div class="g-stat">
          <span class="g-stat-num">{{ glucoseSummary.count }}</span>
          <span class="g-stat-lbl">记录次数</span>
        </div>
        <div class="g-stat">
          <span class="g-stat-num">{{ glucoseSummary.avg ?? '-' }}</span>
          <span class="g-stat-lbl">平均 mmol/L</span>
        </div>
        <div class="g-stat">
          <span class="g-stat-num">{{ glucoseSummary.max ?? '-' }}</span>
          <span class="g-stat-lbl">最高</span>
        </div>
        <div class="g-stat">
          <span class="g-stat-num">{{ glucoseSummary.min ?? '-' }}</span>
          <span class="g-stat-lbl">最低</span>
        </div>
      </div>
      <div ref="glucoseChartRef" class="glucose-chart" />
      <p class="chart-hint">灰色区域为参考正常范围 3.9–6.1 mmol/L（空腹）</p>
    </div>

    <template #bottom>
      <div class="ck-records-sheet ck-records-sheet--solo">
        <div class="ck-records-sheet__head">
          <h2>{{ dateDisplay.main }} 记录</h2>
          <span>{{ glucoseRecords.length }} 条</span>
        </div>
        <div class="ck-records-list ck-records-list--tall">
          <div v-for="r in glucoseRecords" :key="r.checkin_id" class="ck-record-row">
            <div class="ck-record-body">
              <div class="glucose-value-row">
                <strong class="glucose-value">{{ r.glucose_value }}</strong>
                <span class="glucose-unit">mmol/L</span>
                <span class="glucose-status" :class="'status-' + r.status">{{ glucoseStatusLabel(r.status) }}</span>
              </div>
              <div class="ck-record-meta">{{ r.measure_context_label }} · {{ formatTime(r.record_time) }}</div>
            </div>
          </div>
          <p v-if="!glucoseRecords.length" class="ck-empty-tip">当日暂无血糖记录</p>
        </div>
      </div>
    </template>
  </CheckinTypeLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import CheckinTypeLayout from './components/CheckinTypeLayout.vue'
import CheckinDateBar from './components/CheckinDateBar.vue'
import { useCheckinDate } from './composables/useCheckinDate'
import { GLUCOSE_STATUS_LABELS } from './checkin/constants'
import { formatTime } from './checkin/utils'
import {
  createGlucoseCheckin,
  getGlucoseRecords,
  getGlucoseHistory,
} from '@/api/checkin'

const { checkinDate, dateDisplay } = useCheckinDate()

const submitting = ref(false)
const glucoseRecords = ref([])
const glucoseForm = ref({ value: 5.6, context: 4 })
const glucoseTrendDays = ref(14)
const glucoseHistory = ref({ records: [], summary: {} })
const glucoseChartRef = ref(null)
let glucoseChart = null

const glucoseSummary = computed(() => glucoseHistory.value.summary || {})

onMounted(async () => {
  await Promise.all([loadRecords(), loadGlucoseHistory()])
})

watch(checkinDate, () => {
  loadRecords()
  loadGlucoseHistory()
})

watch(glucoseTrendDays, loadGlucoseHistory)

onUnmounted(() => {
  glucoseChart?.dispose()
  glucoseChart = null
})

async function loadRecords() {
  glucoseRecords.value = await getGlucoseRecords(checkinDate.value)
}

async function loadGlucoseHistory() {
  const end = checkinDate.value
  const start = dayjs(end).subtract(glucoseTrendDays.value - 1, 'day').format('YYYY-MM-DD')
  try {
    glucoseHistory.value = await getGlucoseHistory({ start_date: start, end_date: end })
    await nextTick()
    renderGlucoseChart()
  } catch (e) {
    glucoseHistory.value = { records: [], summary: { count: 0 } }
    await nextTick()
    renderGlucoseChart()
    ElMessage.error(e.message || '加载血糖趋势失败')
  }
}

function glucoseStatusLabel(status) {
  return GLUCOSE_STATUS_LABELS[status] || GLUCOSE_STATUS_LABELS.unknown
}

function formatChartLabel(record) {
  if (!record.record_time) return record.checkin_date || ''
  return dayjs(record.record_time).format('M/D HH:mm')
}

function renderGlucoseChart() {
  if (!glucoseChartRef.value) return
  if (!glucoseChart) glucoseChart = echarts.init(glucoseChartRef.value)

  const records = glucoseHistory.value.records || []
  const labels = records.map(formatChartLabel)
  const values = records.map((r) => Number(r.glucose_value))

  glucoseChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter(params) {
        const idx = params[0]?.dataIndex
        const r = records[idx]
        if (!r) return ''
        return `${r.measure_context_label}<br/>${r.glucose_value} mmol/L<br/>${dayjs(r.record_time).format('YYYY-MM-DD HH:mm')}`
      },
    },
    grid: { left: 40, right: 16, top: 24, bottom: 36 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { fontSize: 10, rotate: labels.length > 8 ? 35 : 0 },
    },
    yAxis: {
      type: 'value',
      name: 'mmol/L',
      min: records.length ? (v) => Math.max(0, Math.floor((v.min ?? 3) - 1)) : 0,
      max: records.length ? (v) => Math.ceil((v.max ?? 8) + 1) : 12,
    },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { color: '#ef4444', width: 2 },
      itemStyle: { color: '#ef4444' },
      markArea: {
        silent: true,
        itemStyle: { color: 'rgba(148, 163, 184, 0.18)' },
        data: [[{ yAxis: 3.9 }, { yAxis: 6.1 }]],
      },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { type: 'dashed', color: '#94a3b8' },
        data: [{ yAxis: 6.1, label: { formatter: '6.1', fontSize: 10 } }],
      },
    }],
  }, true)
}

async function submitGlucose() {
  const v = glucoseForm.value.value
  if (v == null) return ElMessage.warning('请填写血糖值')
  if (v > 20 || v < 2) {
    try { await ElMessageBox.confirm(`血糖值 ${v} mmol/L 异常，确认提交？`, '异常确认') } catch { return }
  }
  submitting.value = true
  try {
    const res = await createGlucoseCheckin({
      checkin_date: checkinDate.value,
      glucose_value: v,
      measure_context: glucoseForm.value.context,
    })
    ElMessage.success(`打卡成功！+${res.points_earned || 15} 积分`)
    await Promise.all([loadRecords(), loadGlucoseHistory()])
  } catch (e) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
@import './checkin/checkin-theme.css';

.ck-type-page__scroll {
  padding-bottom: 220px;
}

.glucose-input-card {
  background: var(--ck-card);
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--ck-shadow);
  margin-top: 8px;
}

.glucose-input-card h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ck-text);
}

.submit-btn { width: 100%; margin-top: 4px; }

.compact-form :deep(.el-button--primary) {
  --el-button-bg-color: #ef4444;
  --el-button-border-color: #ef4444;
}

.glucose-trend-section {
  margin-top: 16px;
  background: var(--ck-card);
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--ck-shadow);
}

.trend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.trend-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.glucose-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.g-stat {
  text-align: center;
  padding: 8px 4px;
  background: #fef2f2;
  border-radius: 10px;
}

.g-stat-num {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #dc2626;
}

.g-stat-lbl {
  font-size: 10px;
  color: var(--ck-text-soft);
}

.glucose-chart {
  width: 100%;
  height: 220px;
}

.chart-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--ck-text-soft);
  text-align: center;
}

.ck-records-sheet--solo {
  margin: 0 16px 12px;
  border-radius: 16px;
  box-shadow: var(--ck-shadow-lg);
}

.ck-records-list--tall {
  max-height: 180px;
}

.glucose-value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}

.glucose-value {
  font-size: 20px;
  font-weight: 800;
  color: #dc2626;
}

.glucose-unit {
  font-size: 12px;
  color: var(--ck-text-soft);
}

.glucose-status {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 600;
}

.glucose-status.status-normal { background: #ecfdf5; color: #059669; }
.glucose-status.status-low { background: #dbeafe; color: #2563eb; }
.glucose-status.status-elevated { background: #fffbeb; color: #d97706; }
.glucose-status.status-high { background: #fef2f2; color: #dc2626; }
</style>
