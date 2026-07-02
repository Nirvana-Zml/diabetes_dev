<template>
  <div class="admin-page">
    <header class="admin-header">
      <div class="admin-header-inner">
        <div>
          <h1>管理后台</h1>
          <p>{{ APP_NAME }} · 内容运营</p>
        </div>
        <div class="header-actions">
          <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
        </div>
      </div>
    </header>

    <main class="admin-main">
      <section v-loading="statsLoading" class="overview-section">
        <h2 class="section-heading">数据概览</h2>
        <div class="overview-grid">
          <div v-for="card in overviewCards" :key="card.label" class="overview-card">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
          </div>
        </div>
      </section>

      <h2 class="section-heading">功能模块</h2>
      <div class="module-grid">
        <div
          v-for="mod in modules"
          :key="mod.path"
          class="module-card"
          @click="router.push(mod.path)"
        >
          <div class="module-icon" :style="{ background: mod.color }">
            <el-icon :size="28"><component :is="mod.icon" /></el-icon>
          </div>
          <div class="module-body">
            <h3>{{ mod.title }}</h3>
            <p>{{ mod.desc }}</p>
          </div>
          <el-icon class="module-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Document, VideoCamera, DataAnalysis, ArrowRight } from '@element-plus/icons-vue'
import { clearTokens } from '@/api/auth'
import { getStatsOverview } from '@/api/stats'
import { APP_NAME } from '@/config'

const router = useRouter()
const statsLoading = ref(false)
const overview = ref(null)

const modules = [
  {
    title: '数据统计',
    desc: '用户注册、打卡、问诊、健康档案与内容阅读等多维度运营分析',
    path: '/statistics',
    icon: DataAnalysis,
    color: 'linear-gradient(135deg, #f59e0b, #d97706)',
  },
  {
    title: '资讯管理',
    desc: '创建、编辑、审核健康资讯，支持 AI 辅助生成初稿',
    path: '/articles',
    icon: Document,
    color: 'linear-gradient(135deg, #14b8a6, #0d9488)',
  },
  {
    title: '科普视频管理',
    desc: '上传科普视频与封面，自动解析时长并同步至首页展示',
    path: '/videos',
    icon: VideoCamera,
    color: 'linear-gradient(135deg, #6366f1, #4f46e5)',
  },
]

const overviewCards = computed(() => {
  const o = overview.value
  if (!o) {
    return [
      { label: '注册用户', value: '-' },
      { label: '近 7 日活跃', value: '-' },
      { label: '打卡总数', value: '-' },
      { label: '问诊会话', value: '-' },
    ]
  }
  return [
    { label: '注册用户', value: o.users?.total ?? 0 },
    { label: '近 7 日活跃', value: o.users?.active_week ?? 0 },
    { label: '打卡总数', value: o.checkin?.total ?? 0 },
    { label: '问诊会话', value: o.consultation?.total_sessions ?? 0 },
  ]
})

async function loadOverview() {
  statsLoading.value = true
  try {
    overview.value = await getStatsOverview()
  } catch {
    overview.value = null
  } finally {
    statsLoading.value = false
  }
}

async function handleLogout() {
  await ElMessageBox.confirm('确定退出管理后台？', '提示', { type: 'warning' })
  clearTokens()
  router.push('/login')
}

onMounted(loadOverview)
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
  align-items: center;
  gap: 16px;
}
.admin-header h1 {
  margin: 0;
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
}
.admin-main {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 32px var(--edge-padding) 64px;
}
.overview-section {
  margin-bottom: 32px;
}
.section-heading {
  margin: 0 0 20px;
  font-size: 18px;
  color: var(--warm-700);
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.overview-card {
  background: #fff;
  border: 1px solid var(--warm-100);
  border-radius: 14px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.overview-card span {
  font-size: 13px;
  color: var(--warm-400);
}
.overview-card strong {
  font-size: 30px;
  color: var(--warm-800);
}
.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.module-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border-radius: 16px;
  border: 1px solid var(--warm-100);
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.module-card:hover {
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}
.module-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.module-body h3 {
  margin: 0 0 6px;
  font-size: 17px;
  color: var(--warm-800);
}
.module-body p {
  margin: 0;
  font-size: 13px;
  color: var(--warm-400);
  line-height: 1.5;
}
.module-arrow {
  margin-left: auto;
  color: var(--warm-300);
}
</style>
