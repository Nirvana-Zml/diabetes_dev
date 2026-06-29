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
      <div class="chat-input">
        <el-input
          v-model="query"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="输入糖尿病相关问题，如：可以吃水果吗？"
          @keydown.ctrl.enter="send"
        />
        <el-button type="primary" :loading="streaming" :disabled="!query.trim()" @click="send">发送</el-button>
      </div>
      <DisclaimerBar class="chat-disclaimer" />
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { chatQA } from '@/api/chat'
import MarkdownContent from './MarkdownContent.vue'
import DisclaimerBar from './DisclaimerBar.vue'

const visible = defineModel({ type: Boolean, default: false })
const query = ref('')
const messages = ref([])
const streaming = ref(false)
const conversationId = ref('')
const msgRef = ref()

watch(visible, (v) => {
  if (v && messages.value.length === 0) {
    messages.value.push({
      role: 'assistant',
      content: '您好，我是小糖助手，可以为您解答糖尿病科普问题。请问有什么可以帮您？',
    })
  }
})

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
}

.chat-disclaimer {
  margin-top: 12px;
}
</style>
