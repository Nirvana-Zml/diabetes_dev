<template>
  <SiteLayout
    :title="isChat ? '' : '医师咨询'"
    :show-back="!isChat"
    :fill-height="isChat"
    :full-bleed="isChat"
  >
    <!-- 医生列表 -->
    <div v-if="!isChat" class="page-container">
      <div class="filter-bar section-card">
        <el-select v-model="filterDept" placeholder="科室" clearable style="width: 120px" @change="loadDoctors">
          <el-option v-for="dept in departments" :key="dept" :label="dept" :value="dept" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="在线状态" clearable style="width: 120px" @change="loadDoctors">
          <el-option label="在线" value="online" />
          <el-option label="离线" value="offline" />
          <el-option label="忙碌" value="busy" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索医生" clearable style="flex:1" @keyup.enter="loadDoctors">
          <template #append><el-button @click="loadDoctors">搜索</el-button></template>
        </el-input>
      </div>

      <div v-for="doc in doctors" :key="doc.doctor_id" class="section-card doctor-row">
        <el-avatar :size="56" :src="doc.avatar_url || doctorAvatarUrl(doc.doctor_id)" />
        <div class="doc-info">
          <div class="doc-header">
            <span class="name">{{ doc.name }}</span>
            <el-tag :type="statusType(doc.status)" size="small">{{ statusText(doc.status) }}</el-tag>
          </div>
          <div class="meta">{{ doc.title }} · {{ doc.department }} · {{ doc.hospital }}</div>
          <div class="meta">问诊 {{ doc.consultation_count }} 次 · 评分 {{ doc.rating }}</div>
          <p class="intro">{{ doc.introduction }}</p>
        </div>
        <el-button type="primary" @click="openChat(doc)">
          {{ consultBtnText(doc.status) }}
        </el-button>
      </div>
    </div>

    <!-- 咨询会话 -->
    <div v-else class="chat-page">
      <div class="chat-toolbar">
        <button type="button" class="chat-back-btn" aria-label="返回" @click="router.back()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
          </svg>
        </button>
      </div>

      <header class="doctor-card">
        <div class="doctor-card__main">
          <img
            :src="currentDoctor?.avatar_url || doctorAvatarUrl(currentDoctor?.doctor_id)"
            :alt="currentDoctor?.name"
            class="doctor-card__avatar"
          />
          <div class="doctor-card__info">
            <h2 class="doctor-card__name">
              {{ currentDoctor?.name }}
              <span class="doctor-card__title">{{ currentDoctor?.title }}</span>
            </h2>
            <p class="doctor-card__dept">{{ currentDoctor?.department }} · {{ currentDoctor?.hospital }}</p>
            <div class="doctor-card__status">
              <span class="status-dot" :class="`status-dot--${currentDoctor?.status || 'offline'}`" />
              <span>{{ statusText(currentDoctor?.status) }} · AI 模拟医生</span>
            </div>
          </div>
        </div>
        <button type="button" class="doctor-card__end" @click="showRate = true">结束咨询</button>
      </header>

      <!-- AI 辅助建议 -->
      <div class="ai-panel" :class="{ collapsed: aiPanelCollapsed }">
        <div class="ai-panel-header" @click="aiPanelCollapsed = !aiPanelCollapsed">
          <div class="ai-panel-title">
            <div class="ai-icon">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a2 2 0 1 1 0 4h-1v1a2 2 0 1 1-4 0v-1h-2v1a2 2 0 1 1-4 0v-1H9a2 2 0 1 1 0-4h1v-1a7 7 0 0 1 7-7h1V5.73c-.6-.34-1-.99-1-1.73a2 2 0 0 1 2-2z"/></svg>
            </div>
            <span>AI 辅助建议</span>
            <span class="ai-badge">DeepSeek</span>
          </div>
          <svg class="ai-panel-arrow" :class="{ collapsed: aiPanelCollapsed }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m18 15-6-6-6 6"/></svg>
        </div>
        <div v-show="!aiPanelCollapsed" class="ai-panel-body">
          <div class="ai-suggest-card">
            <template v-if="hasAiSuggestion">
              <div v-if="aiSuggestion.possibleDiagnoses?.length" class="ai-block">
                <div class="ai-block-label">可能方向</div>
                <div class="tag-row">
                  <span v-for="(d, i) in aiSuggestion.possibleDiagnoses" :key="i" class="diag-tag" :class="d.probability">
                    {{ d.name }}
                    <em>{{ probText(d.probability) }}</em>
                  </span>
                </div>
              </div>
              <div v-if="aiSuggestion.recommendedExams?.length" class="ai-block">
                <div class="ai-block-label">建议检查</div>
                <p class="ai-block-text">{{ aiSuggestion.recommendedExams.join(' · ') }}</p>
              </div>
              <div v-if="aiSuggestion.suggestedQuestions?.length" class="ai-block">
                <div class="ai-block-label">建议追问</div>
                <div class="question-chips">
                  <button
                    v-for="(q, i) in aiSuggestion.suggestedQuestions"
                    :key="i"
                    type="button"
                    class="question-chip"
                    @click="useQuickQuestion(q)"
                  >{{ q }}</button>
                </div>
              </div>
              <p v-if="aiSuggestion.treatmentStrategy" class="ai-strategy">{{ aiSuggestion.treatmentStrategy }}</p>
            </template>
            <p v-else class="ai-empty">发送消息后，系统将结合知识库生成辅助建议</p>
          </div>
          <p class="ai-disclaimer">AI 建议仅供参考，不能替代线下就医</p>
        </div>
      </div>

      <!-- 消息区域 -->
      <div ref="chatRef" class="chat-body">
        <template v-for="(msg, idx) in messages" :key="msg.message_id">
          <div v-if="showDateDivider(idx)" class="date-divider">
            <span>{{ formatDate(msg.sent_at) }}</span>
          </div>
          <div class="msg-group" :class="msg.sender_type">
            <div class="msg-time-line">{{ formatTime(msg.sent_at) }}</div>
            <div class="chat-msg">
              <img
                v-if="msg.sender_type === 'doctor'"
                :src="currentDoctor?.avatar_url || doctorAvatarUrl(currentDoctor?.doctor_id)"
                :alt="currentDoctor?.name"
                class="msg-avatar"
              />
              <div class="bubble" :class="msg.sender_type">
                <MarkdownContent v-if="msg.sender_type === 'doctor'" :content="msg.content" />
                <span v-else>{{ msg.content }}</span>
              </div>
              <img
                v-if="msg.sender_type === 'user'"
                :src="userAvatar"
                alt="我"
                class="msg-avatar user-avatar"
              />
            </div>
          </div>
        </template>

        <!-- 快捷提问（仅欢迎语后） -->
        <div v-if="showQuickQuestions" class="quick-questions">
          <p class="quick-label">您可以这样问：</p>
          <div class="quick-chips">
            <button
              v-for="q in quickQuestions"
              :key="q"
              type="button"
              class="quick-chip"
              :disabled="sending"
              @click="useQuickQuestion(q)"
            >{{ q }}</button>
          </div>
        </div>

        <div v-if="typing" class="msg-group doctor">
          <div class="chat-msg">
            <img
              :src="currentDoctor?.avatar_url || doctorAvatarUrl(currentDoctor?.doctor_id)"
              class="msg-avatar"
              alt=""
            />
            <div class="typing-wrap">
              <div class="typing-bubble">
                <span class="typing-dot" /><span class="typing-dot" /><span class="typing-dot" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-footer">
        <div class="input-row">
          <textarea
            v-model="inputMsg"
            class="msg-input"
            rows="1"
            placeholder="描述您的症状或健康问题…"
            :disabled="sending"
            @keydown.enter.exact.prevent="sendMsg"
          />
          <button type="button" class="send-btn" :disabled="sending || !inputMsg.trim()" @click="sendMsg">
            <svg v-if="!sending" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            <span v-else class="send-spinner" />
          </button>
        </div>
        <p class="footer-tip">按 Enter 发送 · 内容由 AI 生成，仅供参考</p>
      </div>
    </div>

    <!-- 评价弹窗 -->
    <Transition name="fade">
      <div v-if="showRate" class="modal-overlay" @click.self="showRate = false">
        <div class="rate-modal">
          <div class="rate-modal-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2m-6 9 2 2 4-4"/></svg>
          </div>
          <h3>结束咨询</h3>
          <p class="rate-sub">请对本次 {{ currentDoctor?.name }} 医生的咨询服务进行评价</p>
          <div class="star-row">
            <button v-for="n in 5" :key="n" type="button" class="star-btn" @click="rating = n">
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
    </Transition>
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
import { useUserStore } from '@/stores/user'
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
const userStore = useUserStore()
const isChat = computed(() => route.path.includes('/chat'))

const doctors = ref([])
const filterDept = ref('')
const filterStatus = ref('')
const keyword = ref('')
const departments = ref([])
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
const aiPanelCollapsed = ref(true)
const aiSuggestion = ref({})
const ratingTexts = ['非常不满意', '不满意', '一般', '满意', '非常满意']

const quickQuestions = [
  '最近空腹血糖偏高，需要调整用药吗？',
  '糖尿病前期该如何饮食控制？',
  '运动后血糖反而升高，正常吗？',
  '二甲双胍有哪些常见副作用？',
]

const userNickname = computed(() => userStore.nickname)
const userAvatar = computed(() => userStore.profile?.avatar_url || '')

const hasAiSuggestion = computed(() =>
  (aiSuggestion.value.possibleDiagnoses?.length > 0)
  || (aiSuggestion.value.recommendedExams?.length > 0)
  || (aiSuggestion.value.suggestedQuestions?.length > 0)
  || !!aiSuggestion.value.treatmentStrategy,
)

const showQuickQuestions = computed(() =>
  !sending.value && !typing.value && messages.value.length <= 1 && messages.value.every(m => m.sender_type === 'doctor'),
)

onMounted(async () => {
  userStore.fetchProfile()
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
  if (route.path.includes('/chat')) {
    await initChatFromRoute()
  }
})

function resolveDoctor(doctorId) {
  if (!doctorId) return null
  return doctors.value.find((d) => d.doctor_id === doctorId)
}

async function startSession(doc, { updateRoute = false } = {}) {
  currentDoctor.value = doc
  const session = await createConsultation({ doctor_id: doc.doctor_id })
  const sid = session.session_id
  if (!sid) {
    throw new Error('创建会话失败')
  }
  sessionId.value = sid
  messages.value = await getConsultMessages(sid)
  if (updateRoute) {
    await router.replace({
      path: '/consultation/chat',
      query: { doctor_id: doc.doctor_id, session_id: sid },
    })
  }
  await loadAiSuggestion()
  if (hasAiSuggestion.value) aiPanelCollapsed.value = false
  await scrollToBottom()
}

async function ensureSessionId() {
  if (sessionId.value) return sessionId.value
  const querySessionId = route.query.session_id
  if (querySessionId) {
    sessionId.value = querySessionId
    return sessionId.value
  }
  const doc = currentDoctor.value || resolveDoctor(route.query.doctor_id)
  if (doc) {
    await startSession(doc, { updateRoute: true })
    return sessionId.value
  }
  return null
}

async function initChatFromRoute() {
  const querySessionId = route.query.session_id
  const queryDoctorId = route.query.doctor_id

  const doc = resolveDoctor(queryDoctorId)
  if (doc) currentDoctor.value = doc

  if (querySessionId) {
    sessionId.value = querySessionId
    messages.value = await getConsultMessages(querySessionId)
    await loadAiSuggestion()
    if (hasAiSuggestion.value) aiPanelCollapsed.value = false
    await scrollToBottom()
    return
  }

  if (doc) {
    await startSession(doc, { updateRoute: true })
  }
}

async function loadDoctors() {
  doctors.value = await getDoctors({
    department: filterDept.value,
    status: filterStatus.value,
    keyword: keyword.value,
  })
}

async function loadAiSuggestion() {
  if (!sessionId.value) return
  try {
    aiSuggestion.value = await getAiSuggest(sessionId.value)
    if (hasAiSuggestion.value) aiPanelCollapsed.value = false
  } catch {
    aiSuggestion.value = {}
  }
}

function consultBtnText(status) {
  return { online: '立即咨询', offline: '留言咨询', busy: '排队中' }[status] || '立即咨询'
}

function statusType(s) {
  return { online: 'success', offline: 'info', busy: 'warning' }[s] || 'info'
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

function formatDate(t) {
  const d = dayjs(t)
  if (d.isSame(dayjs(), 'day')) return '今天'
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天'
  return d.format('M月D日')
}

function showDateDivider(idx) {
  if (idx === 0) return true
  const cur = dayjs(messages.value[idx].sent_at).format('YYYY-MM-DD')
  const prev = dayjs(messages.value[idx - 1].sent_at).format('YYYY-MM-DD')
  return cur !== prev
}

function useQuickQuestion(q) {
  inputMsg.value = q
  sendMsg()
}

async function openChat(doc) {
  if (doc.status === 'offline') {
    ElMessage.info('医生当前不在线，可留言，医生上线后将回复')
  }
  await startSession(doc, { updateRoute: true })
}

async function sendMsg() {
  const content = inputMsg.value.trim()
  if (!content || sending.value) return

  const pendingId = `pending_${Date.now()}`
  const pendingMsg = {
    message_id: pendingId,
    sender_type: 'user',
    content,
    sent_at: new Date().toISOString(),
  }

  sending.value = true
  inputMsg.value = ''
  messages.value.push(pendingMsg)
  await nextTick()
  await scrollToBottom()
  typing.value = true

  try {
    const sid = await ensureSessionId()
    if (!sid) {
      messages.value = messages.value.filter((m) => m.message_id !== pendingId)
      ElMessage.error('会话未就绪，请返回后重新进入咨询')
      inputMsg.value = content
      return
    }
    const { userMessage, aiMessage } = await sendConsultMessage(sid, { content })
    const idx = messages.value.findIndex((m) => m.message_id === pendingId)
    if (idx >= 0) {
      messages.value[idx] = userMessage
    } else {
      messages.value.push(userMessage)
    }
    await nextTick()
    await scrollToBottom()
    if (aiMessage) {
      messages.value.push(aiMessage)
      await loadAiSuggestion()
      await nextTick()
      await scrollToBottom()
    }
  } catch (e) {
    messages.value = messages.value.filter((m) => m.message_id !== pendingId)
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
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.doctor-row { display: flex; gap: 12px; align-items: flex-start; }
.doc-info { flex: 1; min-width: 0; }
.doc-header { display: flex; align-items: center; gap: 8px; }
.name { font-weight: 600; font-size: 16px; }
.meta { font-size: 12px; color: #909399; margin-top: 4px; }
.intro { font-size: 13px; color: #606266; margin: 8px 0 0; }

/* ===== Chat ===== */
.chat-page {
  --c-bg: #fafbfc;
  --c-text: #333333;
  --c-muted: #888888;
  --c-border: #e6e8eb;
  --c-moss: #7a9e88;
  --c-moss-deep: #6a8772;
  --c-moss-light: #c2d9cb;
  --c-ai-bg: #f3f6f4;
  --c-warn: #c9956a;
  --c-card: #ffffff;

  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  min-height: 520px;
  max-width: 900px;
  margin: 0 auto;
  padding: 0 24px;
  background: var(--c-bg);
  box-sizing: border-box;
}

.chat-toolbar {
  flex-shrink: 0;
  padding: 12px 0 4px;
}

.chat-back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-card);
  color: var(--c-muted);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}

.chat-back-btn:hover {
  border-color: #c5d5cb;
  color: var(--c-text);
}

.chat-back-btn svg {
  width: 18px;
  height: 18px;
}

.doctor-card {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 8px 0 12px;
  padding: 14px 16px;
  background: linear-gradient(180deg, #f7f9f8 0%, #ffffff 100%);
  border: 1px solid var(--c-border);
  border-radius: 14px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}

.doctor-card__main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.doctor-card__avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid var(--c-border);
}

.doctor-card__info { min-width: 0; }

.doctor-card__name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
  line-height: 1.35;
}

.doctor-card__title {
  font-size: 13px;
  font-weight: 400;
  color: var(--c-muted);
}

.doctor-card__dept {
  margin: 2px 0 4px;
  font-size: 12px;
  color: var(--c-muted);
}

.doctor-card__status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--c-muted);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #b0b8b4;
}

.status-dot--online {
  background: var(--c-moss);
  animation: statusPulse 2s infinite;
}

.status-dot--busy { background: var(--c-warn); }
.status-dot--offline { background: #c5cbc7; }

@keyframes statusPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

.doctor-card__end {
  flex-shrink: 0;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--c-muted);
  background: #f5f6f7;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.doctor-card__end:hover {
  background: #eef0f2;
  color: var(--c-text);
}

.ai-panel {
  flex-shrink: 0;
  margin-bottom: 12px;
  background: var(--c-ai-bg);
  border: 1px solid #e8ece9;
  border-radius: 12px;
  overflow: hidden;
}

.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
}

.ai-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text);
}

.ai-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #dce8e0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-moss-deep);
}

.ai-icon svg { width: 12px; height: 12px; }

.ai-badge {
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 500;
  color: var(--c-muted);
  background: rgba(255, 255, 255, 0.7);
  border-radius: 4px;
}

.ai-panel-arrow {
  width: 16px;
  height: 16px;
  color: #b0b8b4;
  transition: transform 0.25s;
}

.ai-panel-arrow.collapsed { transform: rotate(180deg); }

.ai-panel-body { padding: 0 14px 12px; }

.ai-suggest-card {
  background: rgba(255, 255, 255, 0.65);
  border-radius: 10px;
  padding: 14px 16px;
}

.ai-block { margin-bottom: 12px; }
.ai-block:last-child { margin-bottom: 0; }

.ai-block-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--c-muted);
  letter-spacing: 0.3px;
  margin-bottom: 8px;
}

.ai-block-text {
  font-size: 14px;
  color: var(--c-text);
  margin: 0;
  line-height: 1.75;
}

.tag-row { display: flex; flex-wrap: wrap; gap: 6px; }

.diag-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  background: #fff;
  color: var(--c-text);
  border-radius: 8px;
  border: 1px solid var(--c-border);
}

.diag-tag.high {
  border-color: #f0d4c4;
  background: #fdf8f4;
  color: #a06840;
}

.diag-tag.medium {
  border-color: #ede4c8;
  background: #faf8f2;
  color: #8a7344;
}

.diag-tag em {
  font-style: normal;
  font-size: 10px;
  color: var(--c-muted);
}

.question-chips, .quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-chip, .quick-chip {
  padding: 7px 14px;
  font-size: 13px;
  color: var(--c-text);
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 20px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  text-align: left;
  line-height: 1.45;
}

.question-chip:hover, .quick-chip:hover {
  background: var(--c-moss-light);
  border-color: #d4e0d8;
}

.ai-strategy {
  font-size: 14px;
  color: var(--c-text);
  line-height: 1.75;
  margin: 12px 0 0;
  padding-top: 12px;
  border-top: 1px solid #e8ece9;
}

.ai-empty {
  font-size: 13px;
  color: var(--c-muted);
  margin: 0;
  text-align: center;
  padding: 8px 0;
  line-height: 1.65;
}

.ai-disclaimer {
  font-size: 10px;
  color: #b0b8b4;
  text-align: center;
  margin: 10px 0 0;
  line-height: 1.5;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0 16px;
  scroll-behavior: smooth;
}

.date-divider {
  text-align: center;
  margin: 6px 0 14px;
}

.date-divider span {
  font-size: 11px;
  color: var(--c-muted);
}

.msg-group {
  margin-bottom: 14px;
}

.msg-group.user .chat-msg {
  justify-content: flex-end;
}

.msg-time-line {
  text-align: center;
  font-size: 11px;
  color: var(--c-muted);
  margin-bottom: 6px;
  line-height: 1;
}

.chat-msg {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid var(--c-border);
}

.user-avatar {
  background: #eef0f2;
}

.bubble {
  max-width: min(78%, 520px);
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  border-radius: 12px;
}

.bubble.doctor {
  background: var(--c-card);
  color: var(--c-text);
  border: 1px solid var(--c-border);
}

.bubble.user {
  background: var(--c-moss-light);
  color: var(--c-text);
  border: none;
}

.bubble.doctor :deep(.markdown-body) { font-size: 14px; color: var(--c-text); }
.bubble.doctor :deep(.markdown-body p) { margin: 0 0 8px; }
.bubble.doctor :deep(.markdown-body p:last-child) { margin-bottom: 0; }
.bubble.doctor :deep(.markdown-body ul) { padding-left: 1.2em; margin: 4px 0; }

.quick-questions {
  margin: 8px 0 14px;
  padding: 0 12px;
}

.quick-label {
  font-size: 12px;
  color: var(--c-muted);
  margin: 0 0 10px;
  text-align: center;
}

.typing-wrap { padding-bottom: 2px; }

.typing-bubble {
  background: var(--c-card);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 14px 18px;
  display: inline-flex;
  gap: 5px;
}

.typing-dot {
  width: 7px;
  height: 7px;
  background: var(--c-moss);
  border-radius: 50%;
  animation: typingBounce 1.4s infinite ease-in-out both;
}

.typing-dot:nth-child(1) { animation-delay: -0.32s; }
.typing-dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(0.4); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.chat-footer {
  flex-shrink: 0;
  padding: 12px 0 20px;
  background: transparent;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.msg-input {
  flex: 1;
  min-height: 44px;
  max-height: 120px;
  padding: 11px 16px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  color: var(--c-text);
  background: var(--c-card);
  border: 1px solid var(--c-border);
  border-radius: 22px;
  outline: none;
  resize: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.msg-input::placeholder { color: #b0b8b4; }

.msg-input:focus {
  border-color: #c5d5cb;
  box-shadow: 0 0 0 3px rgba(139, 175, 154, 0.12);
}

.send-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: var(--c-moss-deep);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: opacity 0.15s, background 0.15s;
}

.send-btn:hover:not(:disabled) { background: #6f8f7a; }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.send-btn svg { width: 18px; height: 18px; }

.send-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.footer-tip {
  font-size: 10px;
  color: #b0b8b4;
  text-align: center;
  margin: 8px 0 0;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 24px;
}

.rate-modal {
  background: #fff;
  border-radius: 24px;
  padding: 28px 24px;
  width: 100%;
  max-width: 380px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}

.rate-modal-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  background: #f3f6f4;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7d9e88;
}

.rate-modal-icon svg { width: 32px; height: 32px; }

.rate-modal h3 {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 6px;
}

.rate-sub {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 18px;
}

.star-row {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-bottom: 8px;
}

.star-btn {
  width: 44px;
  height: 44px;
  border: none;
  background: none;
  cursor: pointer;
  padding: 0;
  transition: transform 0.15s;
}

.star-btn:hover { transform: scale(1.1); }
.star-btn svg { width: 32px; height: 32px; }

.rating-text {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 16px;
}

.rate-textarea {
  width: 100%;
  padding: 12px 14px;
  font-size: 14px;
  font-family: inherit;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  resize: none;
  outline: none;
  margin-bottom: 16px;
  box-sizing: border-box;
}

.rate-textarea:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.12);
}

.rate-actions { display: flex; gap: 12px; }

.btn-secondary, .btn-primary {
  flex: 1;
  height: 48px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  border: none;
  cursor: pointer;
}

.btn-secondary { background: #f5f6f7; color: #333333; }
.btn-primary {
  background: #7d9e88;
  color: #fff;
  box-shadow: none;
}
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
