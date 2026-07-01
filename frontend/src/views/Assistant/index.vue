<template>
  <SiteLayout title="" :show-footer="false" fill-height full-bleed>
    <div class="assistant-page">
      <div class="page-inner">
        <div class="chat-shell">
          <div class="chat-messages" ref="msgRef">
            <div v-if="welcomeMsg" class="welcome-block">
              <div class="assistant-avatar assistant-avatar--lg" aria-hidden="true">
                <svg viewBox="0 0 48 48" fill="none">
                  <circle cx="24" cy="24" r="24" fill="url(#botGrad)" />
                  <rect x="14" y="18" width="20" height="16" rx="4" fill="#fff" fill-opacity="0.95" />
                  <circle cx="19" cy="26" r="2.5" fill="#0d9488" />
                  <circle cx="29" cy="26" r="2.5" fill="#0d9488" />
                  <path d="M20 31c1.5 1.5 6.5 1.5 8 0" stroke="#0d9488" stroke-width="1.5" stroke-linecap="round" />
                  <rect x="22" y="12" width="4" height="6" rx="2" fill="#fff" fill-opacity="0.85" />
                  <defs>
                    <linearGradient id="botGrad" x1="8" y1="4" x2="40" y2="44" gradientUnits="userSpaceOnUse">
                      <stop stop-color="#a8e6dc" />
                      <stop offset="1" stop-color="#14b8a6" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
              <div class="welcome-bubble">
                <p class="welcome-label">小糖助手</p>
                <p class="welcome-text">{{ welcomeMsg.content }}</p>
              </div>
            </div>

            <div v-if="!hasUserMessages" class="quick-section">
              <p class="quick-section__title">试试这些问题</p>
              <div class="quick-questions">
                <button
                  v-for="(q, idx) in quickQs"
                  :key="q"
                  type="button"
                  class="quick-q"
                  :disabled="streaming"
                  @click="askQuick(q)"
                >
                  <span class="quick-q__icon">{{ quickIcons[idx] }}</span>
                  <span class="quick-q__text">{{ q }}</span>
                </button>
              </div>
            </div>

            <div
              v-for="(msg, i) in chatMessages"
              :key="i"
              class="msg"
              :class="msg.role"
            >
              <div v-if="msg.role === 'assistant'" class="assistant-avatar" aria-hidden="true">
                <svg viewBox="0 0 32 32" fill="none">
                  <circle cx="16" cy="16" r="16" fill="url(#botGradSm)" />
                  <rect x="9" y="12" width="14" height="11" rx="3" fill="#fff" fill-opacity="0.95" />
                  <circle cx="13" cy="17" r="1.5" fill="#0d9488" />
                  <circle cx="19" cy="17" r="1.5" fill="#0d9488" />
                  <defs>
                    <linearGradient id="botGradSm" x1="4" y1="2" x2="28" y2="30" gradientUnits="userSpaceOnUse">
                      <stop stop-color="#a8e6dc" />
                      <stop offset="1" stop-color="#14b8a6" />
                    </linearGradient>
                  </defs>
                </svg>
              </div>
              <div class="bubble" :class="{ typing: isThinking(msg, i) }">
                <template v-if="msg.role === 'assistant'">
                  <template v-if="isThinking(msg, i)">
                    <span class="typing-dots"><i /><i /><i /></span>
                    正在思考中…
                  </template>
                  <MarkdownContent v-else :content="msg.content" />
                </template>
                <span v-else class="user-text">{{ msg.content }}</span>
              </div>
            </div>
          </div>

          <div class="risk-tip">
            <svg class="risk-tip__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
            <p class="risk-tip__text">{{ disclaimerText }}</p>
          </div>

          <div class="chat-input-bar">
            <div class="input-row">
              <textarea
                v-model="query"
                class="chat-textarea"
                rows="1"
                maxlength="500"
                placeholder="输入您的健康问题…"
                :disabled="streaming"
                @keydown.ctrl.enter.prevent="send"
                @input="autoResize"
                ref="textareaRef"
              />
              <button
                type="button"
                class="send-btn"
                :disabled="!query.trim() || streaming"
                aria-label="发送"
                @click="send"
              >
                <svg class="send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
                </svg>
              </button>
            </div>
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
const textareaRef = ref()

const quickQs = ['糖尿病可以吃水果吗？', '空腹血糖多少正常？', '如何预防糖尿病？', '运动后血糖低怎么办？']
const quickIcons = ['🍎', '📊', '🛡️', '🏃']
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

function isThinking(msg, index) {
  return (
    streaming.value
    && msg.role === 'assistant'
    && !msg.content?.trim()
    && index === chatMessages.value.length - 1
  )
}

onMounted(() => {
  messages.value.push({
    role: 'assistant',
    content: '您好，我是小糖助手，您的糖尿病健康管理 AI 助手。有什么健康疑问，都可以随时问我。',
  })
})

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 120)}px`
}

function askQuick(q) {
  query.value = q
  send()
}

async function send() {
  const q = query.value.trim()
  if (!q || streaming.value) return
  messages.value.push({ role: 'user', content: q })
  query.value = ''
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
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
  --asst-accent-bg: #f0fdfa;
  --asst-accent-border: #ccfbf1;

  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  background: var(--warm-50);
  font-weight: 400;
  letter-spacing: 0.01em;
}

.page-inner {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 820px;
  margin: 0 auto;
  padding: 24px clamp(16px, 4vw, 32px);
  box-sizing: border-box;
}

.chat-shell {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: #fff;
  border-radius: 20px;
  border: 1px solid var(--warm-200);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.chat-messages {
  order: 1;
  flex: 1;
  overflow-y: auto;
  padding: 24px clamp(16px, 3vw, 28px) 20px;
  scrollbar-width: thin;
  scrollbar-color: var(--warm-200) transparent;
}

.chat-messages::-webkit-scrollbar {
  width: 5px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: var(--warm-200);
  border-radius: 4px;
}

.assistant-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.18);
}

.assistant-avatar--lg {
  width: 52px;
  height: 52px;
  box-shadow: 0 4px 14px rgba(13, 148, 136, 0.2);
}

.assistant-avatar svg {
  display: block;
  width: 100%;
  height: 100%;
}

.welcome-block {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 24px;
}

.welcome-bubble {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  background: var(--asst-accent-bg);
  border: 1px solid var(--asst-accent-border);
  border-radius: 4px 20px 20px 20px;
}

.welcome-label {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--health-700);
  letter-spacing: 0.04em;
}

.welcome-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  color: var(--warm-700);
}

.quick-section {
  margin-bottom: 28px;
  padding-left: 66px;
}

.quick-section__title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--warm-500);
}

.quick-questions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-q {
  appearance: none;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--warm-200);
  background: #fff;
  color: var(--warm-700);
  font-size: 13px;
  line-height: 1.45;
  text-align: left;
  border-radius: 14px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
  font-family: inherit;
}

.quick-q:hover:not(:disabled) {
  border-color: var(--asst-accent-border);
  background: var(--asst-accent-bg);
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.08);
}

.quick-q:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.quick-q__icon {
  flex-shrink: 0;
  font-size: 18px;
  line-height: 1.2;
}

.quick-q__text {
  flex: 1;
}

.msg {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
}

.msg.assistant {
  justify-content: flex-start;
}

.msg.user {
  justify-content: flex-end;
  gap: 0;
}

.bubble {
  max-width: min(82%, 560px);
  padding: 12px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;
}

.msg.assistant .bubble {
  background: var(--asst-accent-bg);
  color: var(--warm-700);
  border: 1px solid var(--asst-accent-border);
  border-radius: 4px 18px 18px 18px;
}

.msg.user .bubble {
  background: #c2d9cb;
  color: var(--warm-800);
  border-radius: 18px 4px 18px 18px;
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
  background: var(--health-500);
  animation: dotPulse 1.2s ease-in-out infinite;
}

.typing-dots i:nth-child(2) { animation-delay: 0.15s; }
.typing-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.85); }
  40% { opacity: 1; transform: scale(1); }
}

.msg.assistant .bubble :deep(.markdown-body) {
  font-size: 15px;
  line-height: 1.75;
  color: var(--warm-700);
}

.msg.assistant .bubble :deep(.markdown-body h2) {
  font-size: 16px;
  font-weight: 600;
  color: var(--warm-800);
  margin: 14px 0 8px;
}

.msg.assistant .bubble :deep(.markdown-body strong) {
  color: var(--health-700);
  font-weight: 600;
}

.msg.assistant .bubble :deep(.markdown-body blockquote) {
  margin: 10px 0;
  padding: 10px 14px;
  border-left: 3px solid var(--asst-accent-border);
  background: rgba(255, 255, 255, 0.6);
  color: var(--warm-500);
  border-radius: 0 10px 10px 0;
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

.msg.assistant .bubble :deep(.markdown-body code) {
  background: var(--asst-accent-bg);
  color: var(--health-700);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

.chat-input-bar {
  order: 2;
  flex-shrink: 0;
  padding: 16px clamp(16px, 3vw, 28px) 20px;
  border-top: 1px solid var(--warm-200);
  background: var(--warm-50);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 6px 6px 6px 16px;
  background: #fff;
  border: 1.5px solid var(--warm-200);
  border-radius: 20px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-row:focus-within {
  border-color: var(--health-500);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.chat-textarea {
  flex: 1;
  min-height: 24px;
  max-height: 120px;
  padding: 10px 0;
  font-size: 15px;
  line-height: 1.5;
  color: var(--warm-700);
  background: transparent;
  border: none;
  resize: none;
  outline: none;
  font-family: inherit;
}

.chat-textarea::placeholder {
  color: var(--warm-400);
}

.chat-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--health-500), var(--health-600));
  color: #fff;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.04);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-icon {
  width: 20px;
  height: 20px;
}

.risk-tip {
  order: 3;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 clamp(16px, 3vw, 28px) 16px;
  padding: 10px 14px;
  background: #fffbeb;
  border-radius: 12px;
  border: 1px solid #fde68a;
}

.risk-tip__icon {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 1px;
  color: #d97706;
}

.risk-tip__text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #92400e;
}

@media (max-width: 768px) {
  .page-inner {
    flex: 1;
    max-width: none;
    min-height: 0;
    height: 100%;
    padding: 0;
    overflow: hidden;
  }

  .chat-shell {
    flex: 1;
    min-height: 0;
    height: 100%;
    overflow: hidden;
    background: transparent;
    border: none;
    box-shadow: none;
    border-radius: 0;
  }

  .risk-tip {
    order: 0;
    flex-shrink: 0;
    margin: 0;
    padding: 10px var(--edge-padding);
    border-radius: 0;
    border-left: none;
    border-right: none;
    border-top: none;
  }

  .chat-messages {
    order: 1;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
    padding: 16px var(--edge-padding) 20px;
  }

  .chat-input-bar {
    order: 2;
    flex-shrink: 0;
    position: relative;
    z-index: 10;
    padding: 12px var(--edge-padding);
    padding-bottom: calc(12px + env(safe-area-inset-bottom));
    background: var(--warm-50);
    border-top: 1px solid var(--warm-200);
    box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
  }

  .quick-section {
    padding-left: 0;
  }

  .quick-questions {
    grid-template-columns: 1fr;
  }

  .welcome-block {
    gap: 10px;
  }

  .assistant-avatar--lg {
    width: 44px;
    height: 44px;
  }

  .bubble {
    max-width: 88%;
  }
}
</style>
