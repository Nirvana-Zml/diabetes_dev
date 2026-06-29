<template>
  <SiteLayout
    :title="isChat ? '' : '医师咨询'"
    :show-back="isChat"
    :fill-height="isChat"
    full-bleed
  >
    <!-- 医生列表 -->
    <div v-if="!isChat" class="consult-page">
      <div class="filter-bar">
        <div class="filter-select-wrap">
          <select v-model="filterDept" class="filter-select" @change="loadDoctors">
            <option value="">全部科室</option>
            <option v-for="dept in departments" :key="dept" :value="dept">{{ dept }}</option>
          </select>
        </div>
        <div class="filter-search-wrap">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
          </svg>
          <input
            v-model="keyword"
            type="text"
            class="filter-search"
            placeholder="搜索医生姓名"
            @keyup.enter="loadDoctors"
          />
        </div>
      </div>

      <div class="doctor-list">
        <div
          v-for="doc in doctors"
          :key="doc.doctor_id"
          class="doctor-card"
          :class="{ offline: doc.status === 'offline' }"
          @click="openChat(doc)"
        >
          <div class="doctor-avatar-wrap">
            <img :src="doc.avatar_url || doctorAvatarUrl(doc.doctor_id)" :alt="doc.name" class="doctor-avatar" :class="{ grayscale: doc.status === 'offline' }" />
            <span class="status-dot" :class="doc.status" />
          </div>
          <div class="doctor-info">
            <div class="doctor-header">
              <h3 class="doctor-name">{{ doc.name }}</h3>
              <span class="title-badge">{{ doc.title }}</span>
              <span class="status-badge" :class="doc.status">
                <span class="status-badge-dot" />
                {{ statusText(doc.status) }}
              </span>
            </div>
            <div class="doctor-meta">
              <span>{{ doc.department }}</span>
              <span class="sep">|</span>
              <span class="rating">
                <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                {{ doc.rating }}
              </span>
              <span class="sep">|</span>
              <span>咨询 {{ formatCount(doc.consultation_count) }} 次</span>
            </div>
            <p class="doctor-intro">{{ doc.introduction }}</p>
          </div>
        </div>
        <div v-if="!doctors.length && !loading" class="empty-tip">暂无符合条件的医生</div>
      </div>
    </div>

    <!-- 咨询会话 -->
    <div v-else class="chat-page">
      <header class="chat-header">
        <div class="chat-header-doctor">
          <img
            :src="currentDoctor?.avatar_url || doctorAvatarUrl(currentDoctor?.doctor_id)"
            :alt="currentDoctor?.name"
            class="chat-header-avatar"
          />
          <div>
            <h2 class="chat-header-name">{{ currentDoctor?.name }}</h2>
            <div class="chat-header-status">
              <span class="status-badge-dot online" />
              <span>{{ statusText(currentDoctor?.status) }}</span>
            </div>
          </div>
        </div>
        <button type="button" class="chat-menu-btn" aria-label="结束咨询" @click="showRate = true">
          <svg viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>
        </button>
      </header>

      <!-- AI 辅助建议面板 -->
      <div v-if="isChat" class="ai-panel">
        <div class="ai-panel-header" @click="aiPanelCollapsed = !aiPanelCollapsed">
          <div class="ai-panel-title">
            <div class="ai-icon"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a2 2 0 1 1 0 4h-1v1a2 2 0 1 1-4 0v-1h-2v1a2 2 0 1 1-4 0v-1H9a2 2 0 1 1 0-4h1v-1a7 7 0 0 1 7-7h1V5.73c-.6-.34-1-.99-1-1.73a2 2 0 0 1 2-2z"/></svg></div>
            <span>AI 辅助建议</span>
            <span class="ai-badge">DeepSeek</span>
          </div>
          <svg class="ai-panel-arrow" :class="{ collapsed: aiPanelCollapsed }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m18 15-6-6-6 6"/></svg>
        </div>
        <div v-show="!aiPanelCollapsed" class="ai-panel-body">
          <div class="ai-suggest-card">
            <p class="ai-suggest-label">基于对话内容，AI 参考建议：</p>
            <div v-if="aiSuggestion.possibleDiagnoses?.length" class="ai-suggest-section">
              <strong>可能方向：</strong>
              <span v-for="(d, i) in aiSuggestion.possibleDiagnoses" :key="i" class="diag-tag">
                {{ d.name }}（{{ probText(d.probability) }}）
              </span>
            </div>
            <div v-if="aiSuggestion.recommendedExams?.length" class="ai-suggest-section">
              <strong>建议检查：</strong>{{ aiSuggestion.recommendedExams.join('、') }}
            </div>
            <p v-if="aiSuggestion.treatmentStrategy" class="ai-suggest-text">{{ aiSuggestion.treatmentStrategy }}</p>
            <p v-else-if="!hasAiSuggestion" class="ai-suggest-text muted">发送消息后，AI 将生成辅助建议供参考</p>
          </div>
          <p class="ai-disclaimer">AI 建议仅供参考，请以医生诊断为准</p>
        </div>
      </div>

      <!-- 消息区域 -->
      <div ref="chatRef" class="chat-body">
        <div
          v-for="msg in messages"
          :key="msg.message_id"
          class="chat-msg"
          :class="msg.sender_type"
        >
          <img
            v-if="msg.sender_type === 'doctor'"
            :src="currentDoctor?.avatar_url || doctorAvatarUrl(currentDoctor?.doctor_id)"
            :alt="currentDoctor?.name"
            class="msg-avatar"
          />
          <div class="msg-content">
            <div class="msg-meta">
              <span v-if="msg.sender_type === 'doctor'" class="msg-name">{{ currentDoctor?.name }}</span>
              <span class="msg-time">{{ formatTime(msg.sent_at) }}</span>
            </div>
            <div class="bubble" :class="msg.sender_type">
              <MarkdownContent v-if="msg.sender_type === 'doctor'" :content="msg.content" />
              <span v-else>{{ msg.content }}</span>
            </div>
          </div>
        </div>

        <div v-if="typing" class="chat-msg doctor typing-row">
          <img
            :src="currentDoctor?.avatar_url || doctorAvatarUrl(currentDoctor?.doctor_id)"
            class="msg-avatar"
            alt=""
          />
          <div class="typing-bubble">
            <span class="typing-dot" /><span class="typing-dot" /><span class="typing-dot" />
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-footer">
        <div class="input-row">
          <input
            v-model="inputMsg"
            type="text"
            class="msg-input"
            placeholder="请输入消息..."
            :disabled="sending"
            @keyup.enter="sendMsg"
          />
          <button type="button" class="send-btn" :disabled="sending || !inputMsg.trim()" @click="sendMsg">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 评价弹窗 -->
    <div v-if="showRate" class="modal-overlay" @click.self="showRate = false">
      <div class="rate-modal">
        <div class="rate-modal-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2m-6 9 2 2 4-4"/></svg>
        </div>
        <h3>结束咨询</h3>
        <p class="rate-sub">请对本次咨询服务进行评价</p>
        <div class="star-row">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="star-btn"
            @click="rating = n"
          >
            <svg viewBox="0 0 24 24" :fill="n <= rating ? '#fbbf24' : '#e5e7eb'"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
          </button>
        </div>
        <p class="rating-text">{{ ratingTexts[rating - 1] || '请点击星星评分' }}</p>
        <textarea v-model="feedback" class="rate-textarea" placeholder="请输入您的评价（选填）" rows="3" />
        <div class="rate-actions">
          <button type="button" class="btn-secondary" @click="showRate = false">取消</button>
          <button type="button" class="btn-primary" :disabled="!rating" @click="submitClose">提交评价</button>
        </div>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { getDoctors } from '@/api/home'
import { doctorAvatarUrl } from '@/utils/media'
import {
  createConsultation,
  getConsultMessages,
  sendConsultMessage,
  closeConsultation,
  getDepartments,
  getAiSuggest,
} from '@/api/consultation'

const route = useRoute()
const router = useRouter()
const isChat = computed(() => route.path.includes('/chat'))

const doctors = ref([])
const filterDept = ref('')
const keyword = ref('')
const departments = ref([])
const loading = ref(false)
const currentDoctor = ref(null)
const sessionId = ref('')
const messages = ref([])
const inputMsg = ref('')
const sending = ref(false)
const typing = ref(false)
const chatRef = ref()
const showRate = ref(false)
const rating = ref(5)
const feedback = ref('')
const aiPanelCollapsed = ref(false)
const aiSuggestion = ref({})
const ratingTexts = ['非常不满意', '不满意', '一般', '满意', '非常满意']

const hasAiSuggestion = computed(() =>
  (aiSuggestion.value.possibleDiagnoses?.length > 0)
  || (aiSuggestion.value.recommendedExams?.length > 0)
  || !!aiSuggestion.value.treatmentStrategy,
)

onMounted(async () => {
  departments.value = await getDepartments()
  await loadDoctors()
  if (route.path.includes('/chat')) {
    await initChatFromRoute()
  } else if (route.query.doctor_id) {
    const doc = doctors.value.find((d) => d.doctor_id === route.query.doctor_id)
    if (doc) openChat(doc)
  }
})

watch(() => route.fullPath, async () => {
  if (route.path.includes('/chat') && route.query.session_id) {
    await initChatFromRoute()
  }
})

async function initChatFromRoute() {
  sessionId.value = route.query.session_id || ''
  const doc = doctors.value.find((d) => d.doctor_id === route.query.doctor_id)
  if (doc) currentDoctor.value = doc
  if (sessionId.value) {
    messages.value = await getConsultMessages(sessionId.value)
    await loadAiSuggestion()
    await scrollToBottom()
  }
}

async function loadDoctors() {
  loading.value = true
  try {
    doctors.value = await getDoctors({
      department: filterDept.value,
      keyword: keyword.value,
    })
  } finally {
    loading.value = false
  }
}

async function loadAiSuggestion() {
  if (!sessionId.value) return
  try {
    aiSuggestion.value = await getAiSuggest(sessionId.value)
  } catch {
    aiSuggestion.value = {}
  }
}

function formatCount(n) {
  return Number(n || 0).toLocaleString()
}

function statusText(s) {
  return { online: '在线', offline: '离线', busy: '忙碌' }[s] || s || '离线'
}

function probText(p) {
  return { high: '较高', medium: '中等', low: '较低' }[p] || p
}

function formatTime(t) {
  return dayjs(t).format('HH:mm')
}

async function openChat(doc) {
  currentDoctor.value = doc
  if (doc.status === 'offline') {
    ElMessage.info('医生当前不在线，可留言，医生上线后将回复')
  }
  const session = await createConsultation({ doctor_id: doc.doctor_id })
  sessionId.value = session.session_id
  messages.value = await getConsultMessages(sessionId.value)
  router.push({ path: '/consultation/chat', query: { doctor_id: doc.doctor_id, session_id: sessionId.value } })
  await loadAiSuggestion()
  await scrollToBottom()
}

async function sendMsg() {
  const content = inputMsg.value.trim()
  if (!content || sending.value) return
  const sid = sessionId.value || route.query.session_id
  sending.value = true
  typing.value = true
  inputMsg.value = ''
  try {
    const { userMessage, aiMessage } = await sendConsultMessage(sid, { content })
    messages.value.push(userMessage)
    await nextTick()
    await scrollToBottom()
    if (aiMessage) {
      messages.value.push(aiMessage)
      await loadAiSuggestion()
      await nextTick()
      await scrollToBottom()
    }
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
    inputMsg.value = content
  } finally {
    sending.value = false
    typing.value = false
  }
}

async function submitClose() {
  if (!rating.value) {
    ElMessage.warning('请先评分')
    return
  }
  await closeConsultation(sessionId.value || route.query.session_id, {
    rating: rating.value,
    feedback: feedback.value,
  })
  ElMessage.success('咨询已结束，感谢您的评价')
  showRate.value = false
  router.push('/consultation')
}

async function scrollToBottom() {
  await nextTick()
  if (chatRef.value) {
    chatRef.value.scrollTop = chatRef.value.scrollHeight
  }
}
</script>

<style scoped>
.consult-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 0 16px 32px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  padding: 12px 0 16px;
}

.filter-select-wrap,
.filter-search-wrap {
  position: relative;
}

.filter-select-wrap { flex: 1; }
.filter-search-wrap { flex: 1.5; }

.filter-select,
.filter-search {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  font-size: 14px;
  color: #374151;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  outline: none;
}

.filter-select:focus,
.filter-search:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 2px rgba(13, 148, 136, 0.15);
}

.filter-search-wrap .filter-search { padding-left: 36px; }

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  color: #9ca3af;
  pointer-events: none;
}

.doctor-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.doctor-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.15s;
}

.doctor-card:active { transform: scale(0.98); }
.doctor-card.offline { opacity: 0.75; }

.doctor-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.doctor-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  object-position: top;
}

.doctor-avatar.grayscale { filter: grayscale(1); }

.status-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 14px;
  height: 14px;
  border: 2px solid #fff;
  border-radius: 50%;
}

.status-dot.online { background: #22c55e; }
.status-dot.busy { background: #f59e0b; }
.status-dot.offline { background: #9ca3af; }

.doctor-info { flex: 1; min-width: 0; }

.doctor-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.doctor-name {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin: 0;
}

.title-badge {
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
  color: #0f766e;
  background: #f0fdfa;
  border-radius: 999px;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 999px;
}

.status-badge.online { color: #15803d; background: #f0fdf4; }
.status-badge.busy { color: #b45309; background: #fffbeb; }
.status-badge.offline { color: #6b7280; background: #f3f4f6; }

.status-badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.doctor-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.sep { color: #d1d5db; }

.rating {
  display: flex;
  align-items: center;
  gap: 2px;
  color: #374151;
  font-weight: 500;
}

.rating svg { width: 12px; height: 12px; color: #fbbf24; }

.doctor-intro {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-tip {
  text-align: center;
  padding: 48px 0;
  color: #9ca3af;
  font-size: 14px;
}

/* Chat */
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 480px;
  background: #f3f4f6;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 56px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.chat-header-doctor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-header-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.chat-header-name {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin: 0;
  line-height: 1.2;
}

.chat-header-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #15803d;
}

.chat-menu-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  color: #6b7280;
  cursor: pointer;
}

.chat-menu-btn svg { width: 20px; height: 20px; }

.ai-panel {
  background: linear-gradient(to right, #f0fdfa, #ecfdf5);
  border-bottom: 1px solid #ccfbf1;
  flex-shrink: 0;
}

.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
}

.ai-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0f766e;
}

.ai-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #0d9488;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.ai-icon svg { width: 12px; height: 12px; }

.ai-badge {
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 500;
  color: #0d9488;
  background: #ccfbf1;
  border-radius: 4px;
}

.ai-panel-arrow {
  width: 16px;
  height: 16px;
  color: #5eead4;
  transition: transform 0.2s;
}

.ai-panel-arrow.collapsed { transform: rotate(180deg); }

.ai-panel-body { padding: 0 16px 12px; }

.ai-suggest-card {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.ai-suggest-label {
  font-size: 12px;
  color: #6b7280;
  margin: 0 0 8px;
}

.ai-suggest-section {
  font-size: 13px;
  color: #374151;
  margin-bottom: 6px;
  line-height: 1.5;
}

.diag-tag {
  display: inline-block;
  margin: 2px 4px 2px 0;
  padding: 2px 8px;
  font-size: 12px;
  background: #f0fdfa;
  color: #0f766e;
  border-radius: 6px;
}

.ai-suggest-text {
  font-size: 13px;
  color: #374151;
  line-height: 1.6;
  margin: 0;
}

.ai-suggest-text.muted { color: #9ca3af; }

.ai-disclaimer {
  font-size: 11px;
  color: #9ca3af;
  text-align: center;
  margin: 8px 0 0;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-msg {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.chat-msg.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  margin-top: 4px;
}

.msg-content { max-width: 75%; }

.chat-msg.user .msg-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.msg-name { font-size: 12px; color: #6b7280; }
.msg-time { font-size: 12px; color: #9ca3af; }

.bubble {
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.6;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.bubble.doctor {
  background: #fff;
  color: #1f2937;
  border-radius: 16px 16px 16px 4px;
}

.bubble.user {
  background: #0d9488;
  color: #fff;
  border-radius: 16px 16px 4px 16px;
}

.bubble.doctor :deep(.markdown-body) { font-size: 14px; }
.bubble.doctor :deep(.markdown-body p) { margin: 0 0 8px; }
.bubble.doctor :deep(.markdown-body p:last-child) { margin-bottom: 0; }

.typing-bubble {
  background: #fff;
  border-radius: 16px 16px 16px 4px;
  padding: 12px 16px;
  display: flex;
  gap: 4px;
  align-items: center;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.typing-dot {
  width: 8px;
  height: 8px;
  background: #9ca3af;
  border-radius: 50%;
  animation: typingBounce 1.4s infinite ease-in-out both;
}

.typing-dot:nth-child(1) { animation-delay: -0.32s; }
.typing-dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.chat-footer {
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #f3f4f6;
  flex-shrink: 0;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.msg-input {
  flex: 1;
  height: 40px;
  padding: 0 16px;
  font-size: 14px;
  background: #f3f4f6;
  border: none;
  border-radius: 20px;
  outline: none;
}

.msg-input:focus { box-shadow: 0 0 0 2px rgba(13, 148, 136, 0.2); }

.send-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: #0d9488;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.35);
  flex-shrink: 0;
}

.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.send-btn svg { width: 18px; height: 18px; }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 24px;
}

.rate-modal {
  background: #fff;
  border-radius: 20px;
  padding: 24px;
  width: 100%;
  max-width: 360px;
  text-align: center;
}

.rate-modal-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 12px;
  background: #f0fdfa;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0d9488;
}

.rate-modal-icon svg { width: 32px; height: 32px; }

.rate-modal h3 {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 4px;
}

.rate-sub {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 16px;
}

.star-row {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 8px;
}

.star-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: none;
  cursor: pointer;
  padding: 0;
}

.star-btn svg { width: 28px; height: 28px; }

.rating-text {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 16px;
}

.rate-textarea {
  width: 100%;
  padding: 12px;
  font-size: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  resize: none;
  outline: none;
  margin-bottom: 16px;
  box-sizing: border-box;
}

.rate-textarea:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 2px rgba(13, 148, 136, 0.15);
}

.rate-actions {
  display: flex;
  gap: 12px;
}

.btn-secondary,
.btn-primary {
  flex: 1;
  height: 48px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  border: none;
  cursor: pointer;
}

.btn-secondary { background: #f3f4f6; color: #374151; }
.btn-primary { background: #0d9488; color: #fff; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
