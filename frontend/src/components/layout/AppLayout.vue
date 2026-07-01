<template>
  <div
    class="app-layout"
    :class="{
      'app-layout--full': fullWidth,
      'app-layout--with-nav': showNav,
    }"
  >
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
  max-width: 768px;
  margin: 0 auto;
}

.app-layout--full {
  max-width: none;
  width: 100%;
  margin: 0;
}

.app-layout--with-nav {
  padding-bottom: calc(64px + env(safe-area-inset-bottom));
}
</style>
