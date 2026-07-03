import { ref, onMounted, onUnmounted } from 'vue'

export const MOBILE_MAX = 768

export function useIsMobile(maxWidth = MOBILE_MAX) {
  const isMobile = ref(
    typeof window !== 'undefined' && window.matchMedia(`(max-width: ${maxWidth}px)`).matches,
  )
  let mql

  function sync() {
    isMobile.value = window.matchMedia(`(max-width: ${maxWidth}px)`).matches
  }

  onMounted(() => {
    mql = window.matchMedia(`(max-width: ${maxWidth}px)`)
    sync()
    mql.addEventListener('change', sync)
  })

  onUnmounted(() => {
    mql?.removeEventListener('change', sync)
  })

  return isMobile
}
