<template>
  <SiteLayout :title="title" full-bleed hide-header hide-bottom-nav>
    <div class="ck-type-page" :class="accentClass">
      <header class="ck-glass-header">
        <button type="button" class="ck-header-back" aria-label="返回" @click="goBack">
          <el-icon :size="20"><ArrowLeft /></el-icon>
        </button>
        <h1 class="ck-header-title">{{ title }}</h1>
        <slot name="header-action">
          <span class="ck-header-spacer" />
        </slot>
      </header>

      <div v-if="$slots.date" class="ck-date-bar">
        <slot name="date" />
      </div>

      <div class="ck-type-page__scroll" :class="{ 'ck-type-page__scroll--compact': !$slots.bottom }">
        <slot />
      </div>

      <div v-if="$slots.bottom" class="ck-fixed-bottom">
        <slot name="bottom" />
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import SiteLayout from '@/components/layout/SiteLayout.vue'

const props = defineProps({
  title: { type: String, required: true },
  backTo: { type: String, default: '/checkin-records' },
  /** emerald | indigo | amber | red */
  accent: { type: String, default: 'emerald' },
})

const router = useRouter()

const accentClass = computed(() => `ck-type-page--${props.accent}`)

function normalizePath(path) {
  if (!path || typeof path !== 'string') return ''
  return path.split('?')[0]
}

function goBack() {
  const prevPath = normalizePath(window.history.state?.back)
  const hubPath = normalizePath(props.backTo)

  // 从 hub 进入子页时走 history.back，避免 push 重复压栈导致来回跳转
  if (prevPath === hubPath) {
    router.back()
    return
  }

  router.replace(props.backTo)
}
</script>

<style scoped>
@import '../checkin/checkin-theme.css';

.ck-header-spacer {
  width: 40px;
}

.ck-date-bar {
  padding: 0 20px 4px;
}
</style>
