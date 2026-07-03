<template>
  <section class="overview-section" v-loading="loading">
    <div class="overview-head">
      <h2 class="section-heading">审计概览</h2>
      <div class="overview-actions">
        <el-select v-model="trendDays" style="width: 120px" size="small" @change="loadOverview">
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 14 天" :value="14" />
          <el-option label="近 30 天" :value="30" />
        </el-select>
        <el-button size="small" :loading="loading" @click="loadOverview">刷新</el-button>
        <el-button size="small" link type="primary" @click="router.push('/statistics')">
          数据统计
        </el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">今日操作总数</span>
        <strong class="stat-value">{{ today.total }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-label">今日失败数</span>
        <strong class="stat-value stat-value--danger">{{ today.failed }}</strong>
        <span v-if="today.total" class="stat-hint">失败率 {{ failureRate }}%</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">今日成功数</span>
        <strong class="stat-value stat-value--success">{{ today.success }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-label">统计周期</span>
        <strong class="stat-value stat-value--small">近 {{ trendDays }} 天</strong>
      </div>
    </div>

    <div class="panel-row">
      <div class="panel">
        <h3>操作类型占比</h3>
        <div v-if="actionDistribution.length" class="action-mix">
          <div class="pie-wrap">
            <div class="pie-chart" :style="{ background: pieGradient }" />
            <div class="pie-center">
              <strong>{{ actionTotal }}</strong>
              <span>次操作</span>
            </div>
          </div>
          <ul class="pie-legend">
            <li v-for="(item, index) in actionDistribution" :key="item.action">
              <span class="legend-dot" :style="{ background: pieColors[index % pieColors.length] }" />
              <span class="legend-label">{{ item.label }}</span>
              <span class="legend-value">{{ item.count }} ({{ item.percent }}%)</span>
            </li>
          </ul>
        </div>
        <DistributionBars
          v-if="actionBarItems.length"
          :items="actionBarItems"
          empty-text="暂无操作分布数据"
        />
        <div v-else class="panel-empty">暂无操作分布数据</div>
      </div>

      <div class="panel">
        <h3>登录失败趋势</h3>
        <p class="panel-desc">近 {{ trendDays }} 日 user/admin 登录失败次数，用于发现暴力破解风险</p>
        <div v-if="loginTrend.length" class="trend-chart">
          <div v-for="item in loginTrend" :key="item.date" class="trend-col">
            <div class="trend-bar-wrap">
              <div
                class="trend-bar"
                :class="{ 'trend-bar--alert': item.count >= 5 }"
                :style="{ height: trendBarHeight(item.count) }"
                :title="`${item.label}: ${item.count} 次`"
              />
            </div>
            <span class="trend-label">{{ item.label }}</span>
            <span class="trend-count">{{ item.count }}</span>
          </div>
        </div>
        <div v-else class="panel-empty">暂无登录失败记录</div>
      </div>
    </div>

    <div class="panel-row">
      <div class="panel">
        <h3>Top 活跃用户</h3>
        <DistributionBars :items="topUserItems" empty-text="暂无活跃用户数据" />
      </div>
      <div class="panel">
        <h3>Top 活跃管理员</h3>
        <DistributionBars :items="topAdminItems" empty-text="暂无活跃管理员数据" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdminAuditOverview } from '@/api/audit'
import DistributionBars from '@/views/Statistics/DistributionBars.vue'

const router = useRouter()
const loading = ref(false)
const trendDays = ref(7)
const overview = ref(null)

const pieColors = [
  '#0d9488', '#14b8a6', '#6366f1', '#f59e0b', '#ef4444',
  '#8b5cf6', '#ec4899', '#3b82f6', '#84cc16', '#f97316',
]

const today = computed(() => overview.value?.today || { total: 0, failed: 0, success: 0 })

const failureRate = computed(() => {
  const total = today.value.total || 0
  if (!total) return 0
  return Math.round(((today.value.failed || 0) / total) * 100)
})

const actionDistribution = computed(() => {
  const rows = overview.value?.action_distribution || []
  const total = rows.reduce((sum, item) => sum + (Number(item.count) || 0), 0) || 1
  return rows.map((item) => ({
    action: item.action,
    label: item.label || item.action,
    count: Number(item.count) || 0,
    percent: Math.round(((Number(item.count) || 0) / total) * 100),
  }))
})

const actionTotal = computed(() =>
  actionDistribution.value.reduce((sum, item) => sum + item.count, 0),
)

const pieGradient = computed(() => {
  const rows = actionDistribution.value
  if (!rows.length) return 'conic-gradient(#e7e5e4 0deg 360deg)'
  let cursor = 0
  const segments = rows.map((item, index) => {
    const sweep = (item.count / (actionTotal.value || 1)) * 360
    const start = cursor
    cursor += sweep
    return `${pieColors[index % pieColors.length]} ${start}deg ${cursor}deg`
  })
  return `conic-gradient(${segments.join(', ')})`
})

const actionBarItems = computed(() =>
  actionDistribution.value.map((item) => ({
    label: item.label,
    value: item.count,
  })),
)

const loginTrend = computed(() =>
  (overview.value?.login_failure_trend || []).map((item) => ({
    date: item.date,
    label: formatDateLabel(item.date),
    count: Number(item.count) || 0,
  })),
)

const topUserItems = computed(() =>
  (overview.value?.top_users || []).map((item) => ({
    label: item.user_id,
    value: Number(item.count) || 0,
  })),
)

const topAdminItems = computed(() =>
  (overview.value?.top_admins || []).map((item) => ({
    label: item.user_id,
    value: Number(item.count) || 0,
  })),
)

const loginTrendMax = computed(() =>
  Math.max(...loginTrend.value.map((item) => item.count), 1),
)

function formatDateLabel(date) {
  if (!date) return ''
  const text = String(date)
  return text.length >= 10 ? text.slice(5) : text
}

function trendBarHeight(count) {
  const pct = Math.round((count / loginTrendMax.value) * 100)
  return `${Math.max(pct, count > 0 ? 8 : 0)}%`
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await getAdminAuditOverview(trendDays.value)
  } catch (err) {
    ElMessage.error(err?.message || '审计概览加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)

defineExpose({ loadOverview })
</script>

<style scoped>
.overview-section {
  margin-bottom: 16px;
}
.overview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.section-heading {
  margin: 0;
  font-size: 16px;
  color: var(--warm-700);
}
.overview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border: 1px solid var(--warm-100);
  border-radius: 14px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-label {
  font-size: 13px;
  color: var(--warm-400);
}
.stat-value {
  font-size: 28px;
  line-height: 1.2;
  color: var(--warm-800);
}
.stat-value--small {
  font-size: 22px;
}
.stat-value--danger {
  color: #dc2626;
}
.stat-value--success {
  color: #0d9488;
}
.stat-hint {
  font-size: 12px;
  color: var(--warm-400);
}
.panel-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.panel {
  background: #fff;
  border: 1px solid var(--warm-100);
  border-radius: 16px;
  padding: 20px;
}
.panel h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--warm-700);
}
.panel-desc {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--warm-400);
  line-height: 1.5;
}
.panel-empty {
  color: var(--warm-400);
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
.action-mix {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
  margin-bottom: 16px;
}
.pie-wrap {
  position: relative;
  width: 160px;
  height: 160px;
  margin: 0 auto;
}
.pie-chart {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}
.pie-center {
  position: absolute;
  inset: 28%;
  background: #fff;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--warm-400);
  box-shadow: inset 0 0 0 1px var(--warm-100);
}
.pie-center strong {
  font-size: 20px;
  color: var(--warm-800);
  line-height: 1.2;
}
.pie-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pie-legend li {
  display: grid;
  grid-template-columns: 10px 1fr auto;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.legend-label {
  color: var(--warm-700);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.legend-value {
  color: var(--warm-400);
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-height: 180px;
  padding-top: 8px;
}
.trend-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.trend-bar-wrap {
  width: 100%;
  height: 120px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.trend-bar {
  width: 70%;
  max-width: 28px;
  background: linear-gradient(180deg, #f59e0b, #d97706);
  border-radius: 6px 6px 2px 2px;
  min-height: 0;
  transition: height 0.2s ease;
}
.trend-bar--alert {
  background: linear-gradient(180deg, #ef4444, #dc2626);
}
.trend-label {
  font-size: 11px;
  color: var(--warm-400);
}
.trend-count {
  font-size: 12px;
  color: var(--warm-700);
  font-weight: 600;
}
@media (max-width: 960px) {
  .panel-row {
    grid-template-columns: 1fr;
  }
  .action-mix {
    grid-template-columns: 1fr;
  }
}
</style>
