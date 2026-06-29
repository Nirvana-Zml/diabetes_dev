<template>
  <div class="app-layout" :class="{ 'app-layout--full': fullWidth }">
    <slot />
    <BottomNav v-if="showNav" />
  </div>
</template>

<script setup>
import BottomNav from './BottomNav.vue'

defineProps({
  showNav: { type: Boolean, default: true },
  fullWidth: { type: Boolean, default: false },
})
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 70px;
  max-width: 768px;
  margin: 0 auto;
}

.app-layout--full {
  max-width: none;
  width: 100%;
  margin: 0;
  padding-bottom: 0;
}
</style>
