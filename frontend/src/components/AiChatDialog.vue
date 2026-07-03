<template>
  <el-dialog
    v-model="visible"
    title="AI 科普助手"
    width="92%"
    :style="{ maxWidth: '480px' }"
    destroy-on-close
    @closed="onClosed"
  >
    <div class="chat-messages" ref="msgRef">
      <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
        <div class="bubble">
          <MarkdownContent v-if="msg.role === 'assistant'" :content="msg.content" />
          <span v-else>{{ msg.content }}</span>
        </div>
      </div>
      <div v-if="streaming" class="msg assistant">
        <div class="bubble typing">正在思考中…</div>
      </div>
    </div>
    <template #footer>
      <div class="chat-input" :class="{ 'chat-input--recording': recording, 'chat-input--transcribing': transcribing }">
        <VoiceStatusBar :recording="recording" :transcribing="transcribing" />
        <div class="input-row">
          <el-button
            class="mic-btn"
            :class="{ 'is-recording': recording, 'is-transcribing': transcribing }"
            circle
            :disabled="streaming || transcribing"
            :aria-label="recording ? '停止录音' : transcribing ? '正在识别' : '语音输入'"
            @click="toggleVoice"
          >
            <span v-if="recording" class="mic-recording-ring" />
            <span v-if="transcribing" class="mic-spinner" />
            <span v-else-if="recording" class="mic-stop-icon">■</span>
            <span v-else>🎤</span>
          </el-button>
          <el-input
            v-model="query"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :placeholder="recording ? '正在聆听您的声音…' : transcribing ? '正在识别语音…' : '输入糖尿病相关问题，如：可以吃水果吗？'"
            :disabled="streaming || recording || transcribing"
            @keydown.ctrl.enter="send"
          />
          <el-button type="primary" :loading="streaming" :disabled="!query.trim()" @click="send">发送</el-button>
        </div>
      </div>
      <DisclaimerBar class="chat-disclaimer" />
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { chatQA, voiceToText } from '@/api/chat'
import { useVoiceInput } from '@/composables/useVoiceInput'
import MarkdownContent from './MarkdownContent.vue'
import DisclaimerBar from './DisclaimerBar.vue'
import VoiceStatusBar from './VoiceStatusBar.vue'

const visible = defineModel({ type: Boolean, default: false })
const query = ref('')
const messages = ref([])
const streaming = ref(false)
const transcribing = ref(false)
const conversationId = ref('')
const msgRef = ref()
const { recording, start: startVoice, stop: stopVoice } = useVoiceInput()

watch(visible, (v) => {
  if (v && messages.value.length === 0) {
    messages.value.push({
      role: 'assistant',
      content: '您好，我是小糖助手，可以为您解答糖尿病科普问题。请问有什么可以帮您？',
    })
  }
})

async function toggleVoice() {
  if (streaming.value || transcribing.value) return
  if (!recording.value) {
    try {
      await startVoice()
    } catch {
      ElMessage.warning('无法访问麦克风，请检查浏览器权限')
    }
    return
  }
  transcribing.value = true
  try {
    const blob = await stopVoice()
    if (!blob?.size) {
      ElMessage.warning('录音时间过短，请重试')
      return
    }
    const result = await voiceToText(blob)
    if (result?.text) {
      query.value = result.text
    }
  } catch (err) {
    ElMessage.error(err.message || '语音识别失败')
  } finally {
    transcribing.value = false
  }
}

async function send() {
  const q = query.value.trim()
  if (!q || streaming.value) return
  messages.value.push({ role: 'user', content: q })
  query.value = ''
  streaming.value = true
  let answer = ''
  const idx = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })

  await nextTick()
  msgRef.value?.scrollTo({ top: msgRef.value.scrollHeight, behavior: 'smooth' })

  try {
    await chatQA(q, {
      conversationId: conversationId.value,
      onChunk: (data) => {
        answer += data.content
        messages.value[idx].content = answer
        nextTick(() => msgRef.value?.scrollTo({ top: msgRef.value.scrollHeight }))
      },
      onEnd: (data) => {
        conversationId.value = data.conversation_id
        streaming.value = false
      },
    })
  } catch {
    messages.value[idx].content = '服务暂时繁忙，请稍后重试。'
    streaming.value = false
  }
}

function onClosed() {
  query.value = ''
}
</script>

<style scoped>
.chat-messages {
  max-height: 360px;
  overflow-y: auto;
  padding: 8px 0;
}

.msg {
  display: flex;
  margin-bottom: 12px;
}

.msg.user {
  justify-content: flex-end;
}

.msg.user .bubble {
  background: #0d9488;
  color: #fff;
  border-radius: 12px 12px 4px 12px;
}

.msg.assistant .bubble {
  background: #f5f7fa;
  border-radius: 12px 12px 12px 4px;
  max-width: 90%;
}

.bubble {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.5;
}

.typing {
  color: #909399;
}

.chat-input {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px;
  border-radius: 12px;
  transition: background 0.25s;
}

.chat-input--recording {
  background: linear-gradient(180deg, #fff5f5 0%, transparent 100%);
}

.chat-input--transcribing {
  background: linear-gradient(180deg, #f0fdfa 0%, transparent 100%);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.input-row :deep(.el-textarea) {
  flex: 1;
}

.mic-btn {
  position: relative;
  overflow: visible;
}

.mic-btn.is-recording {
  background: #ef4444 !important;
  border-color: #ef4444 !important;
  color: #fff !important;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.25);
  animation: micBtnPulse 1.5s ease-in-out infinite;
}

.mic-btn.is-transcribing {
  background: #f0fdfa !important;
  border-color: #99f6e4 !important;
}

.mic-recording-ring {
  position: absolute;
  inset: -4px;
  border: 2px solid rgba(239, 68, 68, 0.35);
  border-radius: 50%;
  animation: micRing 1.2s ease-out infinite;
  pointer-events: none;
}

.mic-stop-icon {
  position: relative;
  z-index: 1;
  font-size: 12px;
  line-height: 1;
}

.mic-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid #99f6e4;
  border-top-color: #0d9488;
  border-radius: 50%;
  animation: micSpin 0.8s linear infinite;
}

@keyframes micSpin {
  to { transform: rotate(360deg); }
}

@keyframes micBtnPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

@keyframes micRing {
  0% { transform: scale(0.95); opacity: 0.8; }
  100% { transform: scale(1.35); opacity: 0; }
}

.chat-disclaimer {
  margin-top: 12px;
}
</style>
