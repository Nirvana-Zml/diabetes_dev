import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getArticleAudio } from '@/api/article'

/**
 * 资讯详情页「听全文」播放控制。
 * 首次播放时请求后端生成/获取 qwen3-tts-flash 合成的 WAV 并缓存至 MinIO。
 */
export function useArticleAudio() {
  const loading = ref(false)
  const playing = ref(false)
  const audioUrl = ref('')
  let audio = null

  function cleanup() {
    if (audio) {
      audio.pause()
      audio.src = ''
      audio = null
    }
    playing.value = false
  }

  onUnmounted(cleanup)

  async function toggle(articleId) {
    if (!articleId) return

    if (playing.value) {
      audio?.pause()
      playing.value = false
      return
    }

    if (audio && audioUrl.value) {
      await audio.play()
      playing.value = true
      return
    }

    loading.value = true
    try {
      const data = await getArticleAudio(articleId)
      const url = data.audio_url || data.audioUrl
      if (!url) {
        throw new Error('未获取到朗读音频')
      }
      cleanup()
      audioUrl.value = url
      audio = new Audio(url)
      audio.addEventListener('ended', () => { playing.value = false })
      audio.addEventListener('pause', () => { playing.value = false })
      audio.addEventListener('play', () => { playing.value = true })
      await audio.play()
    } catch (err) {
      const msg = err.message || ''
      if (/timeout|超时/i.test(msg)) {
        ElMessage.error('语音合成时间较长，请保持页面打开并重试（长文约需 1～3 分钟）')
      } else {
        ElMessage.error(msg || '语音朗读加载失败')
      }
      cleanup()
    } finally {
      loading.value = false
    }
  }

  function stop() {
    cleanup()
    audioUrl.value = ''
  }

  return { loading, playing, toggle, stop }
}
