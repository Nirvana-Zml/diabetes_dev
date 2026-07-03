<template>
  <section class="ck-date-section">
    <button type="button" class="ck-date-nav" aria-label="上一天" @click="changeDate(-1)">
      <el-icon><ArrowLeft /></el-icon>
    </button>
    <div class="ck-date-center">
      <span v-if="isToday" class="ck-date-badge">今天</span>
      <span class="ck-date-main">{{ dateDisplay.full }}</span>
      <span class="ck-date-weekday">{{ dateDisplay.weekday }}</span>
    </div>
    <button type="button" class="ck-date-nav" :disabled="isToday" aria-label="下一天" @click="changeDate(1)">
      <el-icon><ArrowRight /></el-icon>
    </button>
  </section>
</template>

<script setup>
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useCheckinDate } from '../composables/useCheckinDate'

const { isToday, dateDisplay, changeDate } = useCheckinDate()
</script>

<style scoped>
@import '../checkin/checkin-theme.css';

.ck-date-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 8px 0 4px;
}

.ck-date-nav {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: var(--ck-inactive);
  color: var(--ck-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.ck-date-nav:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.ck-date-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.ck-date-badge {
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--ck-emerald);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.ck-date-main {
  font-size: 15px;
  font-weight: 600;
  color: var(--ck-text);
}

.ck-date-weekday {
  font-size: 13px;
  color: var(--ck-text-soft);
}
</style>
