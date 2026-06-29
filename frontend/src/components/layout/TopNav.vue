<template>
  <header class="top-nav">
    <div class="nav-left" @click="goHome">
      <div class="logo-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 21s-6-4.35-6-10a6 6 0 0 1 12 0c0 5.65-6 10-6 10z" />
        </svg>
      </div>
      <span class="logo-text">{{ title }}</span>
    </div>
    <div class="nav-right">
      <slot name="right">
        <el-icon v-if="showBack" class="nav-btn" :size="20" @click="goBack"><ArrowLeft /></el-icon>
      </slot>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

defineProps({
  title: { type: String, default: '糖尿病智能助手' },
  showBack: { type: Boolean, default: false },
})

const router = useRouter()

function goHome() {
  router.push('/home')
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid #f0f2f5;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.logo-icon {
  width: 32px;
  height: 32px;
  color: #0d9488;
}

.logo-icon svg {
  width: 100%;
  height: 100%;
}

.logo-text {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.nav-btn {
  cursor: pointer;
  padding: 4px;
  color: #606266;
}
</style>
