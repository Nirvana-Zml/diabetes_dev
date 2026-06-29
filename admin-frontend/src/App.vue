<template>
  <div :class="layoutClass">
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const layoutClass = computed(() => (route.meta.layout === 'auth' ? 'app-root' : 'app-main'))
</script>

<style>
.app-main {
  min-height: 100vh;
  background: var(--page-bg, #fafaf9);
}
</style>
