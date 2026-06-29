<template>
  <SiteLayout title="" :show-footer="false" fill-height>
    <div class="assistant-page">
      <div class="chat-shell">
        <div class="chat-messages" ref="msgRef">
          <!-- 欢迎语 -->
          <div v-if="welcomeMsg" class="welcome-block">
            <div class="bot-avatar" aria-hidden="true">
              <svg viewBox="0 0 48 48" fill="none">
                <circle cx="24" cy="24" r="24" fill="url(#botGrad)" />
                <rect x="14" y="18" width="20" height="16" rx="4" fill="#fff" fill-opacity="0.95" />
                <circle cx="19" cy="26" r="2.5" fill="#5bbfb0" />
                <circle cx="29" cy="26" r="2.5" fill="#5bbfb0" />
                <path d="M20 31c1.5 1.5 6.5 1.5 8 0" stroke="#5bbfb0" stroke-width="1.5" stroke-linecap="round" />
                <rect x="22" y="12" width="4" height="6" rx="2" fill="#fff" fill-opacity="0.8" />
                <defs>
                  <linearGradient id="botGrad" x1="8" y1="4" x2="40" y2="44" gradientUnits="userSpaceOnUse">
                    <stop stop-color="#a8e6dc" />
                    <stop offset="1" stop-color="#5bbfb0" />
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <div class="welcome-bubble">
              <p class="welcome-text">{{ welcomeMsg.content }}</p>
            </div>
          </div>

          <!-- 快捷提问 -->
          <div v-if="!hasUserMessages" class="quick-questions">
            <button
              v-for="q in quickQs"
              :key="q"
              type="button"
              class="quick-q"
              :disabled="streaming"
              @click="askQuick(q)"
            >
              {{ q }}
            </button>
          </div>

          <!-- 对话消息 -->
          <div
            v-for="(msg, i) in chatMessages"
            :key="i"
            class="msg"
            :class="msg.role"
          >
            <div class="bubble">
              <MarkdownContent v-if="msg.role === 'assistant'" :content="msg.content" />
              <span v-else class="user-text">{{ msg.content }}</span>
            </div>
          </div>

          <!-- 流式回复中 -->
          <div v-if="streaming" class="msg assistant">
            <div class="bubble typing">
              <span class="typing-dots"><i /><i /><i /></span>
              正在思考中…
            </div>
          </div>
        </div>

        <!-- 底部输入区 -->
        <div class="chat-footer">
          <div class="input-wrap">
            <textarea
              v-model="query"
              class="chat-textarea"
              rows="3"
              maxlength="500"
              placeholder="输入您的健康问题，我会尽力为您解答…"
              :disabled="streaming"
              @keydown.ctrl.enter.prevent="send"
            />
          </div>
          <button
            type="button"
            class="send-btn"
            :disabled="!query.trim() || streaming"
            @click="send"
          >
            <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
            </svg>
            <span>{{ streaming ? '回复中…' : '发送' }}</span>
          </button>
          <div class="risk-tip">
            <svg class="risk-tip__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
            <p class="risk-tip__text">{{ disclaimerText }}</p>
          </div>
        </div>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { chatQA } from '@/api/chat'
import { DISCLAIMER } from '@/config'

const messages = ref([])
const query = ref('')
const streaming = ref(false)
const conversationId = ref('')
const msgRef = ref()

const quickQs = ['糖尿病可以吃水果吗？', '空腹血糖多少正常？', '如何预防糖尿病？', '运动后血糖低怎么办？']
const disclaimerText = DISCLAIMER.replace(/^⚠️\s*/, '')

const welcomeMsg = computed(() =>
  messages.value[0]?.role === 'assistant' ? messages.value[0] : null,
)
const chatMessages = computed(() =>
  welcomeMsg.value ? messages.value.slice(1) : messages.value,
)
const hasUserMessages = computed(() =>
  messages.value.some((m) => m.role === 'user'),
)

onMounted(() => {
  messages.value.push({
    role: 'assistant',
    content: '您好，我是小糖助手，您的糖尿病健康管理 AI 助手。有什么健康疑问，都可以随时问我。',
  })
})

function askQuick(q) {
  query.value = q
  send()
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
        conversationId.value = data.conversation_id || data.conversationId || conversationId.value
      },
    })
  } catch {
    messages.value[idx].content = '服务暂时繁忙，请稍后重试。'
  } finally {
    streaming.value = false
  }
}
</script>

<style scoped>
.assistant-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  background: #fcfcfb;
  font-weight: 400;
  letter-spacing: 0.01em;
}

.chat-shell {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  padding: 0 clamp(20px, 5vw, 48px);
  box-sizing: border-box;
}

/* ── 对话滚动区 ── */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 32px 0 24px;
  scrollbar-width: thin;
  scrollbar-color: var(--warm-200) transparent;
}

.chat-messages::-webkit-scrollbar {
  width: 4px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: var(--warm-200);
  border-radius: 4px;
}

/* ── 欢迎气泡 ── */
.welcome-block {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 28px;
}

.bot-avatar {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(91, 191, 176, 0.18);
}

.bot-avatar svg {
  display: block;
  width: 100%;
  height: 100%;
}

.welcome-bubble {
  flex: 1;
  max-width: calc(100% - 62px);
  padding: 18px 22px;
  background: #fff;
  border-radius: 4px 20px 20px 20px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.04);
}

.welcome-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  color: var(--warm-700);
  font-weight: 400;
}

/* ── 快捷提问 ── */
.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 36px;
  padding-left: 62px;
}

.quick-q {
  appearance: none;
  border: 1px solid var(--warm-200);
  background: var(--warm-100);
  color: var(--warm-600);
  font-size: 13px;
  font-weight: 400;
  line-height: 1.4;
  padding: 8px 18px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, color 0.2s;
  font-family: inherit;
}

.quick-q:hover:not(:disabled) {
  background: #ecf8f6;
  border-color: #b8e4dc;
  color: var(--health-700);
}

.quick-q:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ── 消息气泡 ── */
.msg {
  display: flex;
  margin-bottom: 20px;
}

.msg.assistant {
  justify-content: flex-start;
}

.msg.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 82%;
  padding: 14px 20px;
  border-radius: 20px;
  font-size: 15px;
  line-height: 1.8;
  word-break: break-word;
}

.msg.assistant .bubble {
  background: #ecf8f6;
  color: var(--warm-700);
  border-radius: 4px 20px 20px 20px;
}

.msg.user .bubble {
  background: #e8eef4;
  color: var(--warm-700);
  border-radius: 20px 4px 20px 20px;
}

.user-text {
  display: block;
}

.bubble.typing {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--warm-400);
  font-size: 14px;
}

.typing-dots {
  display: inline-flex;
  gap: 3px;
  align-items: center;
}

.typing-dots i {
  display: block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #b8e4dc;
  animation: dotPulse 1.2s ease-in-out infinite;
}

.typing-dots i:nth-child(2) { animation-delay: 0.15s; }
.typing-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.85); }
  40% { opacity: 1; transform: scale(1); }
}

/* Markdown 内容适配 */
.msg.assistant .bubble :deep(.markdown-body) {
  font-size: 15px;
  line-height: 1.8;
  color: var(--warm-700);
}

.msg.assistant .bubble :deep(.markdown-body h2) {
  font-size: 16px;
  font-weight: 500;
  color: var(--warm-700);
  margin: 16px 0 8px;
}

.msg.assistant .bubble :deep(.markdown-body strong) {
  color: var(--health-700);
  font-weight: 500;
}

.msg.assistant .bubble :deep(.markdown-body blockquote) {
  margin: 10px 0;
  padding: 10px 14px;
  border-left: 2px solid #b8e4dc;
  background: rgba(255, 255, 255, 0.55);
  color: var(--warm-500);
  border-radius: 0 8px 8px 0;
}

.msg.assistant .bubble :deep(.markdown-body ul),
.msg.assistant .bubble :deep(.markdown-body ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.msg.assistant .bubble :deep(.markdown-body p) {
  margin: 0 0 10px;
}

.msg.assistant .bubble :deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

/* ── 底部输入区 ── */
.chat-footer {
  flex-shrink: 0;
  padding: 16px 0 28px;
  border-top: 1px solid var(--warm-200);
  background: #fcfcfb;
}

.input-wrap {
  margin-bottom: 12px;
}

.chat-textarea {
  display: block;
  width: 100%;
  box-sizing: border-box;
  padding: 14px 18px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--warm-700);
  background: #fff;
  border: 1px solid var(--warm-200);
  border-radius: 16px;
  resize: none;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.chat-textarea::placeholder {
  color: var(--warm-400);
}

.chat-textarea:hover:not(:disabled) {
  border-color: #b8e4dc;
}

.chat-textarea:focus {
  border-color: #5bbfb0;
  box-shadow: 0 0 0 3px rgba(91, 191, 176, 0.1);
}

.chat-textarea:disabled {
  background: var(--warm-50);
  cursor: not-allowed;
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 13px 24px;
  border: none;
  border-radius: 14px;
  background: #5bbfb0;
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: #4aad9e;
}

.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.send-icon {
  width: 18px;
  height: 18px;
}

/* ── 风险提示 ── */
.risk-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 14px;
  background: #fef9ee;
  border-radius: 10px;
  border: 1px solid #f5ead6;
}

.risk-tip__icon {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 1px;
  color: #e8a855;
}

.risk-tip__text {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--warm-500);
  font-weight: 400;
}

@media (max-width: 640px) {
  .chat-messages {
    padding-top: 20px;
  }

  .quick-questions {
    padding-left: 0;
  }

  .welcome-block {
    gap: 10px;
  }

  .bot-avatar {
    width: 40px;
    height: 40px;
  }

  .welcome-bubble {
    max-width: calc(100% - 50px);
    padding: 14px 16px;
  }

  .bubble {
    max-width: 90%;
  }
}
</style>
