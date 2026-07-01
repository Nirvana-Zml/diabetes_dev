<template>
  <div :class="layoutClass">
    <router-view />
  </div>
</template>

<script setup>
import { computed, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCheckinReminder } from '@/composables/useCheckinReminder'
import { useMessageCenter } from '@/composables/useMessageCenter'

const route = useRoute()
const userStore = useUserStore()
const layoutClass = computed(() => (route.meta.layout === 'auth' ? 'app-root' : 'app-main'))

let stopReminder = null
let stopMessageCenter = null

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    stopReminder?.()
    stopReminder = null
    stopMessageCenter?.()
    stopMessageCenter = null
    if (loggedIn) {
      const reminder = useCheckinReminder()
      reminder.start()
      stopReminder = reminder.stop

      const messageCenter = useMessageCenter()
      messageCenter.start()
      stopMessageCenter = messageCenter.stop
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  stopReminder?.()
  stopMessageCenter?.()
})
</script>

<style>
.app-main {
  min-height: 100vh;
  background: var(--page-bg, #f5f7fa);
}
</style>
