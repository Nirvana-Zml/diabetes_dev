<template>
  <div class="message-popover">
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
    <div v-if="loading" class="popover-loading">加载中…</div>
    <div v-else-if="!messages.length" class="popover-empty">暂无消息</div>
    <ul v-else class="message-list">
      <li
        v-for="msg in messages"
        :key="msg.message_id || msg.messageId"
        class="message-item"
        :class="{ unread: !(msg.is_read ?? msg.isRead), failed: msg.status === 'failed' }"
      >
        <div class="message-title">{{ msg.title }}</div>
        <div v-if="msg.summary" class="message-summary">{{ msg.summary }}</div>
        <div class="message-meta">{{ formatTime(msg.created_at || msg.createdAt) }}</div>
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
}

.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.popover-title {
  font-weight: 600;
  font-size: 15px;
}

.read-all-btn {
  border: none;
  background: none;
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}

.read-all-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.popover-loading,
.popover-empty {
  padding: 32px 16px;
  text-align: center;
  color: #909399;
  font-size: 14px;
}

.message-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  max-height: 420px;
}

.message-item {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.message-item.unread .message-title::before {
  content: '● ';
  color: var(--el-color-primary, #409eff);
  font-size: 10px;
}

.message-item.failed .message-title {
  color: var(--el-color-warning, #e6a23c);
}

.message-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}

.message-summary {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  margin-bottom: 4px;
}

.message-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.action-btn {
  border: 1px solid var(--el-color-primary, #409eff);
  background: #fff;
  color: var(--el-color-primary, #409eff);
  border-radius: 4px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
}

.action-btn:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
}
</style>
