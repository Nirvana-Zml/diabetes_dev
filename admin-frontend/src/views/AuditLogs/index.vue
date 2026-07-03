<template>
  <div class="admin-page">
    <header class="admin-header">
      <div class="admin-header-inner">
        <div>
          <el-button link type="primary" @click="router.push('/home')">← 返回模块首页</el-button>
          <h1>审计日志</h1>
        </div>
        <div class="header-actions">
          <el-button :loading="exporting" @click="handleExport">导出 CSV</el-button>
          <el-button
            type="danger"
            plain
            :disabled="!selectedIds.length"
            @click="handleBatchDelete"
          >
            批量删除 ({{ selectedIds.length }})
          </el-button>
        </div>
      </div>
    </header>

    <main class="admin-main">
      <AuditOverview ref="overviewRef" />

      <div class="toolbar section-card">
        <div class="quick-filters">
          <el-button
            size="small"
            :type="activePreset === 'today' ? 'primary' : 'default'"
            plain
            @click="applyPreset('today')"
          >
            今天
          </el-button>
          <el-button
            size="small"
            :type="activePreset === 'week' ? 'primary' : 'default'"
            plain
            @click="applyPreset('week')"
          >
            近 7 天
          </el-button>
          <el-button
            size="small"
            :type="activePreset === 'failed' ? 'primary' : 'default'"
            plain
            @click="applyPreset('failed')"
          >
            仅失败
          </el-button>
          <el-button
            size="small"
            :type="activePreset === 'login' ? 'primary' : 'default'"
            plain
            @click="applyPreset('login')"
          >
            仅登录相关
          </el-button>
        </div>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索用户/操作/资源"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
        />
        <el-input
          v-model="filters.userId"
          placeholder="用户 ID"
          clearable
          style="width: 160px"
          @keyup.enter="loadList"
        />
        <el-select
          v-model="filters.action"
          placeholder="操作类型"
          clearable
          filterable
          style="width: 200px"
          @change="onActionChange"
        >
          <el-option
            v-for="item in actionOptions"
            :key="item"
            :label="getAuditActionLabel(item)"
            :value="item"
          />
        </el-select>
        <el-select v-model="filters.result" placeholder="结果" clearable style="width: 120px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="activePreset = ''"
        />
        <el-button @click="loadList">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <div class="section-card" v-loading="loading">
        <el-table
          :data="list"
          stripe
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="log_id" label="日志 ID" width="180" show-overflow-tooltip />
          <el-table-column label="用户 ID" width="150">
            <template #default="{ row }">
              <el-popover
                placement="right"
                :width="280"
                trigger="click"
                @show="loadUserBrief(row.user_id)"
              >
                <template #reference>
                  <el-button link type="primary" class="user-id-link">{{ row.user_id }}</el-button>
                </template>
                <div v-loading="userBriefLoading === row.user_id" class="user-brief">
                  <template v-if="userBriefCache[row.user_id]">
                    <p><strong>角色：</strong>{{ roleLabel(userBriefCache[row.user_id].role) }}</p>
                    <p><strong>用户名：</strong>{{ userBriefCache[row.user_id].username || '—' }}</p>
                    <p><strong>昵称：</strong>{{ userBriefCache[row.user_id].nickname || '—' }}</p>
                    <p v-if="userBriefCache[row.user_id].phone">
                      <strong>手机号：</strong>{{ userBriefCache[row.user_id].phone }}
                    </p>
                    <p v-if="userBriefCache[row.user_id].email">
                      <strong>邮箱：</strong>{{ userBriefCache[row.user_id].email }}
                    </p>
                    <el-button
                      v-if="userBriefCache[row.user_id].role === 'user'"
                      link
                      type="primary"
                      @click="goStatistics(row.user_id)"
                    >
                      查看数据统计
                    </el-button>
                  </template>
                  <p v-else-if="userBriefLoading !== row.user_id" class="user-brief-empty">暂无用户信息</p>
                </div>
              </el-popover>
            </template>
          </el-table-column>
          <el-table-column label="操作类型" width="150">
            <template #default="{ row }">
              <span>{{ getAuditActionLabel(row.action) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="resource" label="操作资源" min-width="140" show-overflow-tooltip />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 1 ? 'success' : 'danger'" size="small">
                {{ row.result === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ip_address" label="IP" width="130" />
          <el-table-column label="操作时间" width="170">
            <template #default="{ row }">{{ formatDate(row.created_at) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!list.length && !loading" class="empty-state">暂无数据</div>
        <div v-if="list.length" class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadList"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </main>

    <el-dialog v-model="detailVisible" title="审计日志详情" width="720px" align-center destroy-on-close>
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="日志 ID">{{ detail.log_id }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ detail.user_id }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          {{ getAuditActionLabel(detail.action) }}
          <span class="action-code">（{{ detail.action }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="操作资源">{{ detail.resource }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="detail.result === 1 ? 'success' : 'danger'" size="small">
            {{ detail.result === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip_address || '—' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ detail.user_agent || '—' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatDate(detail.created_at) }}</el-descriptions-item>
        <el-descriptions-item label="详情">
          <pre class="detail-json">{{ formatDetail(detail.detail) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminAuditLogs,
  getAdminAuditLogDetail,
  getAdminAuditActions,
  deleteAdminAuditLog,
  batchDeleteAdminAuditLogs,
  exportAdminAuditLogs,
} from '@/api/audit'
import { getStatsUserBrief } from '@/api/stats'
import { getAuditActionLabel, LOGIN_ACTIONS } from '@/utils/auditActions'
import AuditOverview from './AuditOverview.vue'

const router = useRouter()
const overviewRef = ref(null)

const loading = ref(false)
const exporting = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const selectedIds = ref([])
const actionOptions = ref([])
const dateRange = ref(null)
const detailVisible = ref(false)
const detail = ref(null)
const activePreset = ref('')
const userBriefCache = ref({})
const userBriefLoading = ref('')

const filters = ref({
  keyword: '',
  userId: '',
  action: '',
  actions: '',
  result: null,
})

function formatDate(value) {
  if (!value) return '—'
  if (Array.isArray(value)) {
    const [y, m, d, h = 0, min = 0, s = 0] = value
    const pad = (n) => String(n).padStart(2, '0')
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(min)}:${pad(s)}`
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatDetail(value) {
  if (value == null || value === '') return '—'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function roleLabel(role) {
  return role === 'admin' ? '管理员' : role === 'user' ? '用户' : role || '—'
}

function formatDateTime(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function startOfDay(date) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

function endOfDay(date) {
  const d = new Date(date)
  d.setHours(23, 59, 59, 0)
  return d
}

function onActionChange() {
  filters.value.actions = ''
  activePreset.value = ''
}

function applyPreset(preset) {
  activePreset.value = preset
  const now = new Date()
  if (preset === 'today') {
    dateRange.value = [formatDateTime(startOfDay(now)), formatDateTime(endOfDay(now))]
    filters.value.result = null
    filters.value.action = ''
    filters.value.actions = ''
  } else if (preset === 'week') {
    const start = new Date(now)
    start.setDate(start.getDate() - 6)
    dateRange.value = [formatDateTime(startOfDay(start)), formatDateTime(endOfDay(now))]
    filters.value.result = null
    filters.value.action = ''
    filters.value.actions = ''
  } else if (preset === 'failed') {
    filters.value.result = 0
    filters.value.action = ''
    filters.value.actions = ''
  } else if (preset === 'login') {
    filters.value.result = null
    filters.value.action = ''
    filters.value.actions = LOGIN_ACTIONS.join(',')
  }
  page.value = 1
  loadList()
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map((row) => row.log_id)
}

function onSizeChange() {
  page.value = 1
  loadList()
}

function resetFilters() {
  filters.value = { keyword: '', userId: '', action: '', actions: '', result: null }
  dateRange.value = null
  activePreset.value = ''
  page.value = 1
  loadList()
}

function buildListParams() {
  const [startTime, endTime] = dateRange.value || []
  return {
    ...filters.value,
    startTime,
    endTime,
    page: page.value,
    size: pageSize.value,
  }
}

async function loadActions() {
  try {
    actionOptions.value = await getAdminAuditActions()
  } catch {
    actionOptions.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await getAdminAuditLogs(buildListParams())
    list.value = data.list
    total.value = data.total
    overviewRef.value?.loadOverview?.()
  } catch (err) {
    list.value = []
    total.value = 0
    ElMessage.error(err?.message || '审计日志加载失败')
  } finally {
    loading.value = false
  }
}

async function loadUserBrief(userId) {
  if (!userId || userBriefCache.value[userId]) return
  userBriefLoading.value = userId
  try {
    userBriefCache.value[userId] = await getStatsUserBrief(userId)
  } catch {
    userBriefCache.value[userId] = null
  } finally {
    userBriefLoading.value = ''
  }
}

function goStatistics(userId) {
  router.push({ path: '/statistics', query: { userId } })
}

async function handleExport() {
  exporting.value = true
  try {
    const [startTime, endTime] = dateRange.value || []
    await exportAdminAuditLogs({
      ...filters.value,
      startTime,
      endTime,
    })
    ElMessage.success('导出成功')
  } catch (err) {
    ElMessage.error(err?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function openDetail(row) {
  try {
    detail.value = await getAdminAuditLogDetail(row.log_id)
    detailVisible.value = true
  } catch (err) {
    ElMessage.error(err?.message || '详情加载失败')
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除日志 ${row.log_id}？`, '删除确认', { type: 'warning' })
  try {
    await deleteAdminAuditLog(row.log_id)
    ElMessage.success('删除成功')
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败')
  }
}

async function handleBatchDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条日志？`, '批量删除', {
    type: 'warning',
  })
  try {
    await batchDeleteAdminAuditLogs(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '批量删除失败')
  }
}

onMounted(async () => {
  await loadActions()
  await loadList()
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
  padding: 20px var(--edge-padding);
}
.admin-header-inner {
  max-width: var(--content-max);
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.admin-header h1 {
  margin: 8px 0 0;
  font-size: 22px;
  color: var(--warm-800);
}
.header-actions {
  display: flex;
  gap: 10px;
}
.admin-main {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 24px var(--edge-padding) 64px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px 20px !important;
}
.quick-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;
  padding-bottom: 4px;
  border-bottom: 1px dashed var(--warm-100);
  margin-bottom: 4px;
}
.section-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid var(--warm-100);
  padding: 20px;
}
.empty-state {
  text-align: center;
  padding: 32px;
  color: var(--warm-400);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.user-id-link {
  font-size: 13px;
  padding: 0;
}
.user-brief p {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--warm-700);
  line-height: 1.5;
}
.user-brief-empty {
  margin: 0;
  font-size: 13px;
  color: var(--warm-400);
}
.action-code {
  margin-left: 6px;
  font-size: 12px;
  color: var(--warm-400);
}
.detail-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--warm-700);
}
</style>
