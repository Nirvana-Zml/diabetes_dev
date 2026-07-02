<template>
  <div class="message-popover" :class="{ 'message-popover--mobile': mobile }">
    <div v-if="mobile" class="sheet-handle" aria-hidden="true" />

    <div class="popover-header">
      <span class="popover-title">消息通知</span>
      <button
        v-if="messages.length"
        type="button"
        class="read-all-btn"
        :disabled="markingAll"
        @click="handleReadAll"
      >
        全部已读
      </button>
    </div>

    <div v-if="loading" class="popover-loading">
      <span class="loading-dot" />
      加载中…
    </div>
    <div v-else-if="!messages.length" class="popover-empty">
      <div class="empty-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path stroke-linecap="round" stroke-linejoin="round" d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0" />
        </svg>
      </div>
      <p>暂无消息</p>
    </div>
    <ul v-else class="message-list">
      <li
        v-for="msg in messages"
        :key="msg.message_id || msg.messageId"
        class="message-item"
        :class="{ unread: !(msg.is_read ?? msg.isRead), failed: msg.status === 'failed' }"
      >
        <div class="message-item__body">
          <div class="message-item__head">
            <span v-if="!(msg.is_read ?? msg.isRead)" class="unread-dot" aria-hidden="true" />
            <div class="message-title">{{ msg.title }}</div>
          </div>
          <div v-if="msg.summary" class="message-summary">{{ msg.summary }}</div>
          <div class="message-meta">{{ formatTime(msg.created_at || msg.createdAt) }}</div>
        </div>
        <button type="button" class="action-btn" @click="handleOpen(msg)">
          {{ actionLabel(msg) }}
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { markAllMessagesRead, markMessageRead } from '@/api/message'
import { useMessageCenter } from '@/composables/useMessageCenter'

defineProps({
  mobile: { type: Boolean, default: false },
})

const emit = defineEmits(['open'])
const router = useRouter()
const { loadList, refresh, messageList } = useMessageCenter()

const loading = ref(true)
const markingAll = ref(false)
const messages = messageList

function formatTime(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const diff = Date.now() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return d.toLocaleDateString('zh-CN')
}

function actionLabel(msg) {
  if (msg.status === 'failed') return '返回重试'
  if ((msg.message_type || msg.messageType) === 'consult_reply') return '进入会话'
  return '查看'
}

function buildRoute(msg) {
  const path = msg.link_path || msg.linkPath || '/home'
  const query = msg.link_query || msg.linkQuery || {}
  return { path, query }
}

async function handleOpen(msg) {
  const id = msg.message_id || msg.messageId
  if (id) {
    try {
      await markMessageRead(id)
    } catch {
      /* ignore */
    }
  }
  emit('open')
  await refresh()
  router.push(buildRoute(msg))
}

async function handleReadAll() {
  markingAll.value = true
  try {
    await markAllMessagesRead()
    await refresh()
    await loadList()
  } finally {
    markingAll.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await loadList()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.message-popover {
  width: 360px;
  max-height: 480px;
  display: flex;
  flex-direction: column;
  --msg-primary: var(--health-600, #0d9488);
  --msg-primary-light: #ccfbf1;
  --msg-primary-soft: rgba(204, 251, 241, 0.55);
  --msg-text: var(--warm-800, #292524);
  --msg-text-secondary: var(--warm-500, #78716c);
  --msg-border: var(--warm-200, #e7e5e4);
}

.message-popover--mobile {
  width: 100%;
  max-height: none;
  height: 100%;
  min-height: 0;
}

.sheet-handle {
  width: 36px;
  height: 4px;
  margin: 10px auto 4px;
  border-radius: 999px;
  background: var(--warm-300, #d6d3d1);
  flex-shrink: 0;
}

.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 14px;
  border-bottom: 1px solid var(--msg-border);
  flex-shrink: 0;
}

.message-popover--mobile .popover-header {
  padding: 4px 20px 14px;
}

.popover-title {
  font-weight: 700;
  font-size: 16px;
  color: var(--msg-text);
}

.read-all-btn {
  border: none;
  background: none;
  color: var(--msg-primary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  padding: 6px 8px;
  margin: -6px -8px -6px 0;
  border-radius: 8px;
  transition: background 0.2s;
}

.read-all-btn:hover:not(:disabled) {
  background: var(--msg-primary-soft);
}

.read-all-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.popover-loading,
.popover-empty {
  padding: 36px 16px;
  text-align: center;
  color: var(--msg-text-secondary);
  font-size: 14px;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.popover-loading {
  flex-direction: row;
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--msg-primary);
  animation: msgPulse 1s ease-in-out infinite;
}

@keyframes msgPulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.empty-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: var(--msg-primary-soft);
  color: var(--msg-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-icon svg {
  width: 26px;
  height: 26px;
}

.popover-empty p {
  margin: 0;
}

.message-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  max-height: 420px;
  flex: 1;
  min-height: 0;
}

.message-popover--mobile .message-list {
  max-height: none;
  padding: 12px 16px calc(16px + env(safe-area-inset-bottom));
  -webkit-overflow-scrolling: touch;
}

.message-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--msg-border);
}

.message-popover--mobile .message-item {
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  margin-bottom: 10px;
  border: 1px solid var(--msg-border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.message-popover--mobile .message-item:last-child {
  margin-bottom: 0;
}

.message-popover--mobile .message-item.unread {
  background: linear-gradient(135deg, #ecfdf5 0%, #f0fdfa 100%);
  border-color: rgba(13, 148, 136, 0.22);
}

.message-item__body {
  flex: 1;
  min-width: 0;
}

.message-item__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.unread-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--msg-primary);
  flex-shrink: 0;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.15);
}

.message-item.failed .message-title {
  color: var(--accent-600, #ea580c);
}

.message-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--msg-text);
  line-height: 1.45;
  margin-bottom: 4px;
}

.message-item__head .message-title {
  margin-bottom: 0;
}

.message-summary {
  font-size: 13px;
  color: var(--msg-text-secondary);
  line-height: 1.5;
  margin: 6px 0 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.message-meta {
  font-size: 12px;
  color: var(--warm-400, #a8a29e);
}

.action-btn {
  flex-shrink: 0;
  border: 1px solid rgba(13, 148, 136, 0.35);
  background: var(--msg-primary-soft);
  color: var(--msg-primary);
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, color 0.2s;
  white-space: nowrap;
}

.message-popover--mobile .action-btn {
  align-self: stretch;
  text-align: center;
  padding: 10px 14px;
  font-size: 13px;
  border-radius: 10px;
  min-height: 40px;
}

.action-btn:hover {
  background: var(--msg-primary-light);
  border-color: var(--msg-primary);
  color: var(--health-700, #0f766e);
}

.action-btn:active {
  transform: scale(0.98);
}
</style>
