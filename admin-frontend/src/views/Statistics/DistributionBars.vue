<template>
  <div class="bars">
    <div v-if="!items.length" class="empty">{{ emptyText }}</div>
    <div v-for="item in normalized" :key="item.label" class="bar-row">
      <span class="bar-label">{{ item.label }}</span>
      <div class="bar-track">
        <div class="bar-fill" :style="{ width: item.percent + '%' }" />
      </div>
      <span class="bar-value">{{ item.value }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无数据' },
})

const normalized = computed(() => {
  const rows = (props.items || []).map((item) => ({
    label: item.label || item.key || '-',
    value: Number(item.value) || 0,
  }))
  const max = Math.max(...rows.map((r) => r.value), 1)
  return rows.map((row) => ({
    ...row,
    percent: Math.round((row.value / max) * 100),
  }))
})
</script>

<style scoped>
.bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.empty {
  color: var(--warm-400);
  font-size: 13px;
  padding: 12px 0;
}
.bar-row {
  display: grid;
  grid-template-columns: 88px 1fr 48px;
  gap: 10px;
  align-items: center;
}
.bar-label {
  font-size: 13px;
  color: var(--warm-700);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar-track {
  height: 10px;
  background: var(--warm-100);
  border-radius: 999px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #14b8a6, #0d9488);
  border-radius: 999px;
  min-width: 2px;
}
.bar-value {
  font-size: 13px;
  color: var(--warm-800);
  text-align: right;
}
</style>
