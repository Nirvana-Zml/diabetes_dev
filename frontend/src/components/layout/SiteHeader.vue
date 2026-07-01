<template>
  <header class="site-header">
    <div class="header-inner" :class="{ 'header-inner--mobile': isMobile }">
      <div class="header-leading">
        <button
          v-if="showMobileBack"
          type="button"
          class="header-back-btn"
          aria-label="返回"
          @click="goBack"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
          </svg>
        </button>
        <div v-else class="header-brand" @click="router.push('/home')">
          <span class="brand-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
            </svg>
          </span>
          <h1 v-if="!isMobile" class="brand-title">{{ APP_NAME }}</h1>
        </div>
      </div>

      <h1 v-if="isMobile && mobileTitle" class="header-mobile-title">{{ mobileTitle }}</h1>

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
        <el-popover
          v-if="loggedIn"
          placement="bottom-end"
          :width="popoverWidth"
          trigger="click"
          @show="onPopoverShow"
        >
          <template #reference>
            <button type="button" class="nav-link bell-btn" aria-label="消息通知">
              <el-badge :value="badgeValue" :hidden="unreadCount <= 0" :max="99">
                <el-icon :size="20"><Bell /></el-icon>
              </el-badge>
            </button>
          </template>
          <MessagePopover @open="() => {}" />
        </el-popover>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { unreadCount, useMessageCenter } from '@/composables/useMessageCenter'
import { useIsMobile } from '@/composables/useBreakpoints'
import { APP_NAME } from '@/config'
import MessagePopover from '@/components/MessageCenter/MessagePopover.vue'

const props = defineProps({
  title: { type: String, default: '' },
})

const router = useRouter()
const route = useRoute()
const { loadList } = useMessageCenter()
const isMobile = useIsMobile()

const popoverWidth = ref(380)

const showMobileBack = computed(() => isMobile.value && route.path !== '/home')

const mobileTitle = computed(() => {
  if (props.title) return props.title
  if (route.path === '/home') return APP_NAME
  return route.meta?.title || ''
})

function syncPopoverWidth() {
  popoverWidth.value = window.innerWidth <= 480 ? Math.min(window.innerWidth - 24, 360) : 380
}

onMounted(() => {
  syncPopoverWidth()
  window.addEventListener('resize', syncPopoverWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', syncPopoverWidth)
})

const loggedIn = computed(() => {
  void route.path
  return isLoggedIn()
})

const badgeValue = computed(() => unreadCount.value)

function onPopoverShow() {
  loadList()
}

const navLinks = computed(() => {
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

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/home')
}
</script>

<style scoped>
.bell-btn {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  margin-left: 4px;
}
</style>
