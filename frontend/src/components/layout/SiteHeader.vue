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
          v-if="loggedIn && !isMobile"
          placement="bottom-end"
          :width="380"
          trigger="click"
          popper-class="message-popover-panel"
          @show="onMessageOpen"
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

        <template v-if="loggedIn && isMobile">
          <button
            type="button"
            class="nav-link bell-btn bell-btn--mobile"
            aria-label="消息通知"
            @click="messageDrawerOpen = true"
          >
            <el-badge :value="badgeValue" :hidden="unreadCount <= 0" :max="99">
              <el-icon :size="20"><Bell /></el-icon>
            </el-badge>
          </button>

          <el-drawer
            v-model="messageDrawerOpen"
            direction="btt"
            size="72%"
            :with-header="false"
            append-to-body
            class="message-sheet"
            @open="onMessageOpen"
          >
            <MessagePopover mobile @open="messageDrawerOpen = false" />
          </el-drawer>
        </template>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { unreadCount, useMessageCenter } from '@/composables/useMessageCenter'
import { useIsMobile } from '@/composables/useBreakpoints'
import { APP_NAME } from '@/config'
import MessagePopover from '@/components/MessageCenter/MessagePopover.vue'
import { safeBack } from '@/utils/navigation'

const props = defineProps({
  title: { type: String, default: '' },
})

const router = useRouter()
const route = useRoute()
const { loadList } = useMessageCenter()
const isMobile = useIsMobile()
const messageDrawerOpen = ref(false)

const showMobileBack = computed(() => isMobile.value && route.path !== '/home')

const mobileTitle = computed(() => {
  if (props.title) return props.title
  if (route.path === '/home') return APP_NAME
  return route.meta?.title || ''
})

const loggedIn = computed(() => {
  void route.path
  return isLoggedIn()
})

const badgeValue = computed(() => unreadCount.value)

function onMessageOpen() {
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
  safeBack('/home')
}
</script>

<style scoped>
.bell-btn {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  margin-left: 4px;
}

.bell-btn--mobile {
  width: 36px;
  height: 36px;
  justify-content: center;
  padding: 0;
  margin-left: 0;
  border: 1px solid var(--warm-200, #e7e5e4);
  border-radius: 10px;
  background: #fff;
  color: var(--warm-600, #57534e);
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.bell-btn--mobile:hover,
.bell-btn--mobile:focus-visible {
  border-color: var(--health-500, #14b8a6);
  color: var(--health-600, #0d9488);
  background: rgba(204, 251, 241, 0.35);
}

.bell-btn--mobile::after {
  display: none;
}
</style>
