<template>
  <AppLayout :show-nav="showMobileNav" full-width>
    <div class="site-page" :class="{ 'site-page--mobile-nav': showMobileNav, 'site-page--no-header': hideHeader }">
      <SiteHeader v-if="!hideHeader" :title="title" />
      <main class="site-main" :class="{ 'site-main--fill': fillHeight, 'site-main--full': fullBleed }">
        <div v-if="!isMobile && (title || showBack)" class="page-toolbar">
          <button v-if="showBack" type="button" class="back-btn" aria-label="返回" @click="safeBack('/home')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
            </svg>
          </button>
          <h1 v-if="title" class="page-toolbar__title">{{ title }}</h1>
        </div>
        <div class="site-content" :class="{ 'site-content--fill': fillHeight, 'site-content--full': fullBleed }">
          <slot />
        </div>
      </main>
      <SiteFooter v-if="showFooter" />
    </div>
  </AppLayout>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from './AppLayout.vue'
import SiteHeader from './SiteHeader.vue'
import SiteFooter from './SiteFooter.vue'
import { useIsMobile } from '@/composables/useBreakpoints'
import { safeBack } from '@/utils/navigation'

const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: false },
  showFooter: { type: Boolean, default: false },
  fillHeight: { type: Boolean, default: false },
  fullBleed: { type: Boolean, default: false },
  /** 手机端是否隐藏底部导航（也可通过 route.meta.hideBottomNav） */
  hideBottomNav: { type: Boolean, default: false },
  /** 隐藏全局顶栏，由页面自行渲染导航（如打卡子页） */
  hideHeader: { type: Boolean, default: false },
})

const route = useRoute()
const isMobile = useIsMobile()

const showMobileNav = computed(() => {
  if (!isMobile.value || props.fillHeight) return false
  if (props.hideBottomNav || route.meta.hideBottomNav) return false
  return true
})
</script>
