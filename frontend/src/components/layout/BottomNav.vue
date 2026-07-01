<template>
  <nav class="bottom-nav">
    <div
      v-for="item in navItems"
      :key="item.path"
      class="nav-item"
      :class="{ active: isActive(item.path) }"
      @click="go(item)"
    >
      <el-icon :size="22"><component :is="item.icon" /></el-icon>
      <span class="nav-label">{{ item.label }}</span>
    </div>
  </nav>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { HomeFilled, Calendar, FirstAidKit, MagicStick, User } from '@element-plus/icons-vue'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const navItems = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/checkin-records', label: '生活打卡', icon: Calendar, requireAuth: true },
  { path: '/health-info', label: '资讯', icon: FirstAidKit },
  { path: '/assistant', label: 'AI助手', icon: MagicStick },
  { path: '/user-center', label: '我的', icon: User, requireAuth: true },
]

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function go(item) {
  const path = item.path
  if (route.path === path) return
  if (item.requireAuth && !isLoggedIn()) {
    router.push(redirectToLogin(path))
    return
  }
  router.push(path)
}
</script>

<style scoped>
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-around;
  align-items: center;
  background: #fff;
  border-top: 1px solid #f0f2f5;
  padding: 6px 0 calc(10px + env(safe-area-inset-bottom));
  z-index: 200;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.04);
  width: 100%;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  color: #909399;
  transition: color 0.2s;
  padding: 2px 12px;
}

.nav-item.active {
  color: #0d9488;
}

.nav-label {
  font-size: 11px;
}
</style>
