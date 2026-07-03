<template>
  <div class="admin-page">
    <header class="admin-header">
      <div class="admin-header-inner">
        <div>
          <el-button link @click="router.push('/home')">← 返回首页</el-button>
          <h1>数据统计</h1>
          <p>用户行为与健康数据运营概览</p>
        </div>
        <div class="header-actions">
          <el-select v-model="trendDays" style="width: 120px" @change="loadTrends">
            <el-option label="近 7 天" :value="7" />
            <el-option label="近 30 天" :value="30" />
            <el-option label="近 90 天" :value="90" />
          </el-select>
          <el-button :loading="loading" @click="reload">刷新</el-button>
        </div>
      </div>
    </header>

    <main class="admin-main" v-loading="loading">
      <el-alert
        v-if="focusedUser"
        type="info"
        :closable="true"
        class="focus-alert"
        @close="clearFocusedUser"
      >
        来自审计日志的用户：{{ focusedUser.nickname || focusedUser.username }}（{{ focusedUser.subject_id }}）
      </el-alert>
      <section class="stat-grid">
        <div v-for="card in summaryCards" :key="card.label" class="stat-card">
          <span class="stat-label">{{ card.label }}</span>
          <strong class="stat-value">{{ card.value }}</strong>
          <span v-if="card.hint" class="stat-hint">{{ card.hint }}</span>
        </div>
      </section>

      <section class="panel-row">
        <div class="panel">
          <h3>用户注册趋势</h3>
          <DistributionBars :items="registrationTrend" empty-text="暂无注册数据" />
        </div>
        <div class="panel">
          <h3>每日打卡趋势</h3>
          <DistributionBars :items="checkinTrend" empty-text="暂无打卡数据" />
        </div>
      </section>

      <section class="panel-row">
        <div class="panel">
          <h3>用户性别分布</h3>
          <DistributionBars :items="distributions.gender" />
        </div>
        <div class="panel">
          <h3>用户年龄段分布</h3>
          <DistributionBars :items="distributions.age_group" />
        </div>
      </section>

      <section class="panel-row">
        <div class="panel">
          <h3>糖尿病分型分布</h3>
          <DistributionBars :items="distributions.diabetes_type" />
        </div>
        <div class="panel">
          <h3>风险等级分布</h3>
          <DistributionBars :items="distributions.risk_level" />
        </div>
      </section>

      <section class="panel-row">
        <div class="panel">
          <h3>打卡类型分布</h3>
          <DistributionBars :items="distributions.checkin_type" />
        </div>
        <div class="panel">
          <h3>消息类型分布</h3>
          <DistributionBars :items="distributions.message_type" />
        </div>
      </section>

      <section class="panel full-width">
        <div class="panel-head">
          <h3>用户数据明细</h3>
          <span class="panel-meta">共 {{ userTotal }} 位用户</span>
        </div>
        <el-table :data="users" stripe empty-text="暂无用户数据">
          <el-table-column prop="username" label="用户名" min-width="100" />
          <el-table-column prop="nickname" label="昵称" min-width="100" />
          <el-table-column prop="phone" label="手机号" min-width="120" />
          <el-table-column prop="gender_label" label="性别" width="70" />
          <el-table-column prop="points" label="积分" width="80" />
          <el-table-column prop="health_record_count" label="健康档案" width="90" />
          <el-table-column prop="risk_assessment_count" label="风险评估" width="90" />
          <el-table-column prop="checkin_count" label="打卡" width="70" />
          <el-table-column prop="consultation_count" label="问诊" width="70" />
          <el-table-column prop="plan_count" label="方案" width="70" />
          <el-table-column prop="article_read_count" label="阅读" width="70" />
          <el-table-column prop="created_at" label="注册时间" min-width="160" />
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="userTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadUsers"
            @size-change="onPageSizeChange"
          />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStatsOverview, getStatsTrends, getStatsUsers, getStatsUserBrief } from '@/api/stats'
import DistributionBars from './DistributionBars.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const focusedUser = ref(null)
const overview = ref(null)
const trends = ref(null)
const users = ref([])
const userTotal = ref(0)
const page = ref(1)
const pageSize = ref(20)
const trendDays = ref(30)

const distributions = computed(() => overview.value?.distributions || {})

const registrationTrend = computed(() =>
  (trends.value?.user_registration || []).map((item) => ({
    label: formatDateLabel(item.date),
    value: Number(item.value) || 0,
  })),
)

const checkinTrend = computed(() =>
  (trends.value?.daily_checkin || []).map((item) => ({
    label: formatDateLabel(item.date),
    value: Number(item.value) || 0,
  })),
)

const summaryCards = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { label: '注册用户', value: o.users?.total ?? 0, hint: `今日新增 ${o.users?.new_today ?? 0}` },
    { label: '近 7 日新增', value: o.users?.new_week ?? 0, hint: `活跃 ${o.users?.active_week ?? 0}` },
    { label: '健康档案', value: o.health?.total_records ?? 0, hint: `评估 ${o.health?.total_assessments ?? 0}` },
    { label: '打卡总数', value: o.checkin?.total ?? 0, hint: `今日 ${o.checkin?.today ?? 0}` },
    { label: '问诊会话', value: o.consultation?.total_sessions ?? 0, hint: `进行中 ${o.consultation?.active_sessions ?? 0}` },
    { label: '问诊消息', value: o.consultation?.total_messages ?? 0, hint: ratingHint(o.consultation?.avg_rating) },
    { label: '健康方案', value: o.plan?.total_plans ?? 0, hint: `覆盖 ${o.plan?.users_with_plans ?? 0} 人` },
    { label: '已发布资讯', value: o.content?.published_articles ?? 0, hint: `阅读 ${o.content?.total_reads ?? 0}` },
    { label: '科普视频', value: o.content?.total_videos ?? 0 },
    { label: '用户消息', value: o.messages?.total ?? 0, hint: `未读 ${o.messages?.unread ?? 0}` },
  ]
})

function ratingHint(rating) {
  return rating == null ? '暂无评分' : `均分 ${rating}`
}

function formatDateLabel(date) {
  if (!date) return ''
  const text = String(date)
  return text.length >= 10 ? text.slice(5) : text
}

async function loadOverview() {
  overview.value = await getStatsOverview()
}

async function loadTrends() {
  trends.value = await getStatsTrends(trendDays.value)
}

async function loadUsers() {
  const data = await getStatsUsers({ page: page.value, size: pageSize.value })
  users.value = data.users || []
  userTotal.value = data.total || 0
}

async function reload() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadTrends(), loadUsers()])
  } catch (err) {
    ElMessage.error(err.message || '加载统计数据失败')
  } finally {
    loading.value = false
  }
}

async function loadFocusedUser() {
  const userId = route.query.userId
  if (!userId || typeof userId !== 'string') {
    focusedUser.value = null
    return
  }
  try {
    focusedUser.value = await getStatsUserBrief(userId)
  } catch {
    focusedUser.value = { subject_id: userId, username: userId, nickname: userId, role: 'unknown' }
  }
}

function clearFocusedUser() {
  focusedUser.value = null
  router.replace({ path: '/statistics' })
}

function onPageSizeChange() {
  page.value = 1
  loadUsers().catch((err) => ElMessage.error(err.message || '加载用户列表失败'))
}

onMounted(() => {
  loadFocusedUser()
  reload()
})
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: var(--warm-50);
}
.admin-header {
  background: #fff;
  border-bottom: 1px solid var(--warm-100);
  padding: 24px var(--edge-padding);
}
.admin-header-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.admin-header h1 {
  margin: 8px 0 0;
  font-size: 24px;
  color: var(--warm-800);
}
.admin-header p {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--warm-400);
}
.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.admin-main {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 24px var(--edge-padding) 64px;
}
.focus-alert {
  margin-bottom: 16px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
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
.stat-hint {
  font-size: 12px;
  color: var(--health-600);
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
.panel.full-width {
  margin-top: 8px;
}
.panel h3 {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--warm-700);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-head h3 {
  margin: 0;
}
.panel-meta {
  font-size: 13px;
  color: var(--warm-400);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
@media (max-width: 960px) {
  .panel-row {
    grid-template-columns: 1fr;
  }
}
</style>
