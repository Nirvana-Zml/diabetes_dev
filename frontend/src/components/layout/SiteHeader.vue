<template>
  <header class="site-header">
    <div class="header-inner">
      <div class="header-brand" @click="router.push('/home')">
        <span class="brand-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
          </svg>
        </span>
        <h1 class="brand-title">糖尿病智能助手</h1>
      </div>
      <nav class="header-nav">
        <button
          v-for="link in navLinks"
          :key="link.path"
          type="button"
          class="nav-link"
          :class="{ active: isNavActive(link.path) }"
          @click="goNav(link)"
        >
          {{ link.label }}
        </button>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const navLinks = computed(() => {
  // 依赖 route.path，登录/登出跳转后刷新导航文案
  void route.path
  const loggedIn = isLoggedIn()
  return [
    { path: '/home', label: '首页' },
    { path: '/health-info', label: '资讯' },
    { path: '/assistant', label: 'AI助手' },
    loggedIn
      ? { path: '/user-center', label: '我的' }
      : { path: '/login', label: '登录/注册' },
  ]
})

function isNavActive(path) {
  if (path === '/home') return route.path === '/home'
  if (path === '/login') {
    return ['/login', '/register', '/forgot-password'].includes(route.path)
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

function goNav(link) {
  if (link.path === '/user-center' && !isLoggedIn()) {
    router.push(redirectToLogin('/user-center'))
    return
  }
  router.push(link.path)
}
</script>
