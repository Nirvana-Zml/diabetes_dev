<template>
  <div class="admin-page">
    <header class="admin-header">
      <div class="admin-header-inner">
        <div>
          <h1>管理后台</h1>
          <p>糖尿病预治智能助手 · 内容运营</p>
        </div>
        <div class="header-actions">
          <el-button @click="goUserPortal">用户端门户</el-button>
          <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
        </div>
      </div>
    </header>

    <main class="admin-main">
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
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Document, ArrowRight } from '@element-plus/icons-vue'
import { clearTokens } from '@/api/auth'
import { USER_PORTAL_URL } from '@/config'

const router = useRouter()

const modules = [
  {
    title: '资讯管理',
    desc: '创建、编辑、审核健康资讯，支持 AI 辅助生成初稿',
    path: '/articles',
    icon: Document,
    color: 'linear-gradient(135deg, #14b8a6, #0d9488)',
  },
]

function goUserPortal() {
  window.open(USER_PORTAL_URL, '_blank')
}

async function handleLogout() {
  await ElMessageBox.confirm('确定退出管理后台？', '提示', { type: 'warning' })
  clearTokens()
  router.push('/login')
}
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
.section-heading {
  margin: 0 0 20px;
  font-size: 18px;
  color: var(--warm-700);
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
