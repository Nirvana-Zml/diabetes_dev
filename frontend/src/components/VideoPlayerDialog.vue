<template>
  <el-dialog
    :model-value="modelValue"
    :title="video?.title || '视频播放'"
    width="min(920px, 96vw)"
    align-center
    destroy-on-close
    class="video-player-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <div class="player-wrap">
      <video
        v-if="video?.url"
        ref="videoRef"
        :key="video.id"
        :src="video.url"
        controls
        autoplay
        playsinline
        class="player"
        @error="loadError = true"
      />
      <div v-else class="player-empty">
        <p>视频暂不可用，请稍后再试</p>
      </div>
      <p v-if="loadError" class="player-error">视频加载失败，请检查网络或联系管理员</p>
    </div>
    <div v-if="video?.duration" class="player-meta">时长 {{ video.duration }}</div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  video: { type: Object, default: null },
})

defineEmits(['update:modelValue'])

const videoRef = ref(null)
const loadError = ref(false)

watch(
  () => props.video?.id,
  () => {
    loadError.value = false
  },
)

function handleClosed() {
  loadError.value = false
  if (videoRef.value) {
    videoRef.value.pause()
    videoRef.value.currentTime = 0
  }
}
</script>

<style scoped>
.player-wrap {
  background: #000;
  border-radius: 12px;
  overflow: hidden;
}

.player {
  display: block;
  width: 100%;
  max-height: 70vh;
  background: #000;
}

.player-empty,
.player-error {
  margin: 0;
  padding: 48px 24px;
  text-align: center;
  color: var(--warm-500);
  font-size: 14px;
}

.player-error {
  padding: 12px 0 0;
  color: #e11d48;
}

.player-meta {
  margin-top: 12px;
  font-size: 13px;
  color: var(--warm-400);
}
</style>

<style>
.video-player-dialog .el-dialog__body {
  padding-top: 8px;
}
</style>
