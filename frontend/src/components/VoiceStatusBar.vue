<template>
  <Transition name="voice-status-fade">
    <div
      v-if="recording || transcribing"
      class="voice-status"
      :class="recording ? 'voice-status--recording' : 'voice-status--transcribing'"
      role="status"
      :aria-live="recording ? 'assertive' : 'polite'"
    >
      <template v-if="recording">
        <span class="voice-status__pulse" aria-hidden="true" />
        <div class="voice-status__wave" aria-hidden="true">
          <span v-for="i in 5" :key="i" class="voice-status__bar" :style="{ animationDelay: `${(i - 1) * 0.12}s` }" />
        </div>
        <span class="voice-status__text">正在录音，再次点击麦克风结束</span>
      </template>
      <template v-else>
        <span class="voice-status__spinner" aria-hidden="true" />
        <span class="voice-status__text voice-status__text--shimmer">正在将语音转为文字…</span>
      </template>
    </div>
  </Transition>
</template>

<script setup>
defineProps({
  recording: { type: Boolean, default: false },
  transcribing: { type: Boolean, default: false },
})
</script>

<style scoped>
.voice-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
}

.voice-status--recording {
  background: linear-gradient(135deg, #fef2f2 0%, #fff1f2 100%);
  border: 1px solid #fecaca;
  color: #b91c1c;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.08);
}

.voice-status--transcribing {
  background: linear-gradient(135deg, #f0fdfa 0%, #ecfeff 100%);
  border: 1px solid #99f6e4;
  color: #0f766e;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.08);
}

.voice-status__pulse {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  animation: voicePulse 1.2s ease-in-out infinite;
}

.voice-status__wave {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 18px;
}

.voice-status__bar {
  width: 3px;
  height: 8px;
  border-radius: 2px;
  background: #f87171;
  animation: voiceBar 0.9s ease-in-out infinite;
}

.voice-status__spinner {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border: 2px solid #99f6e4;
  border-top-color: #0d9488;
  border-radius: 50%;
  animation: voiceSpin 0.75s linear infinite;
}

.voice-status__text {
  flex: 1;
  min-width: 0;
}

.voice-status__text--shimmer {
  background: linear-gradient(
    90deg,
    #0f766e 0%,
    #14b8a6 40%,
    #5eead4 50%,
    #14b8a6 60%,
    #0f766e 100%
  );
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: voiceShimmer 2s linear infinite;
}

.voice-status-fade-enter-active,
.voice-status-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.voice-status-fade-enter-from,
.voice-status-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@keyframes voicePulse {
  0%, 100% { opacity: 1; transform: scale(1); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.45); }
  50% { opacity: 0.85; transform: scale(0.92); box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
}

@keyframes voiceBar {
  0%, 100% { height: 6px; opacity: 0.5; }
  50% { height: 18px; opacity: 1; }
}

@keyframes voiceSpin {
  to { transform: rotate(360deg); }
}

@keyframes voiceShimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
</style>
