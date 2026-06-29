<template>
  <div class="disclaimer">{{ text }}</div>
</template>

<script setup>
import { DISCLAIMER } from '@/config'

defineProps({
  text: { type: String, default: DISCLAIMER },
})
</script>

<style scoped>
.disclaimer {
  font-size: 13px;
  color: var(--warm-500);
  line-height: 1.6;
  padding: 14px 16px;
  background: #fffbeb;
  border-radius: 14px;
  border: 1px solid #fde68a;
}
</style>
