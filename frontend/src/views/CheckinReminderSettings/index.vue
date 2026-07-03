<template>
  <SiteLayout title="打卡提醒" show-back>

    <div class="page-container settings-page" v-loading="loading">
      <div class="section-card intro-card">
        <p class="intro-text">
          在指定时段检查当日是否已完成对应类型打卡；若未完成，将通过应用内消息或浏览器通知提醒您。
        </p>
        <div class="status-list">
          <div class="status-row">
            <span class="status-label">全局打卡提醒</span>
            <div class="status-actions">
              <span class="status-badge" :class="checkinNotify ? 'is-on' : 'is-off'">
                {{ checkinNotify ? '已开启' : '已关闭' }}
              </span>
              <button
                type="button"
                class="text-link"
                @click="router.push({ path: '/user-center', query: { section: 'checkin-notify' } })"
              >
                去修改
              </button>
            </div>
          </div>
          <div v-if="notificationSupported" class="status-row">
            <span class="status-label">浏览器通知</span>
            <div class="status-actions">
              <span class="status-badge" :class="browserBadgeClass">{{ browserNotifyLabel }}</span>
              <button
                v-if="browserNotifyLabel !== '已授权'"
                type="button"
                class="text-link"
                @click="enableBrowserNotify"
              >
                开启
              </button>
            </div>
          </div>
          <p v-else class="hint-muted">当前环境不支持浏览器系统通知（如微信内置浏览器）</p>
        </div>
      </div>

      <div class="type-switcher-wrap">
        <div class="type-switcher" role="tablist" aria-label="提醒类型">
          <button
            v-for="t in typeTabs"
            :key="t.type"
            type="button"
            role="tab"
            class="type-switcher-btn"
            :class="{ active: activeType === String(t.type) }"
            :aria-selected="activeType === String(t.type)"
            @click="activeType = String(t.type)"
          >{{ t.label }}</button>
        </div>
      </div>

      <div
        v-for="t in typeTabs"
        v-show="activeType === String(t.type)"
        :key="t.type"
        class="section-card type-panel"
      >
        <div class="type-head">
          <div class="type-head__text">
            <span class="type-head__title">启用{{ t.label }}提醒</span>
            <span class="type-head__desc">到达设定时间后检查当日是否已打卡</span>
          </div>
          <el-switch v-model="typeEnabled[t.type]" />
        </div>

        <div class="time-slot-list">
          <div
            v-for="(slot, idx) in rulesByType[t.type]"
            :key="`${t.type}-${idx}`"
            class="time-slot-card"
          >
            <div class="time-slot-header">
              <span class="time-slot-index">{{ idx + 1 }}</span>
              <span class="time-slot-label">提醒时段 {{ idx + 1 }}</span>
            </div>
            <div class="time-row">
              <el-time-picker
                v-model="slot.remind_time"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="选择时间"
                :clearable="false"
                editable
                class="time-picker"
              />
              <button
                type="button"
                class="delete-btn"
                :disabled="rulesByType[t.type].length <= 1"
                :title="rulesByType[t.type].length <= 1 ? '至少保留一个时段' : '删除此时段'"
                @click="removeSlot(t.type, idx)"
              >
                <el-icon><Delete /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <button type="button" class="add-slot-btn" @click="addSlot(t.type)">
          <el-icon><Plus /></el-icon>
          <span>添加时段</span>
        </button>
      </div>

      <div class="footer-spacer" />

      <div class="footer-actions">
        <el-button class="footer-btn footer-btn--secondary" @click="applyDefaults">恢复推荐</el-button>
        <el-button type="primary" class="footer-btn" :loading="saving" @click="handleSave">保存设置</el-button>
      </div>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import { getUserProfile } from '@/api/user'
import {
  getReminderRules,
  getReminderDefaults,
  saveReminderRules,
} from '@/api/checkinReminder'
import {
  isNotificationSupported,
  getNotificationPermission,
  requestNotificationPermission,
} from '@/utils/notification'

const router = useRouter()

const TYPE_TABS = [
  { type: 1, label: '饮食' },
  { type: 2, label: '运动' },
  { type: 3, label: '用药' },
  { type: 4, label: '血糖' },
]

const loading = ref(false)
const saving = ref(false)
const activeType = ref('1')
const checkinNotify = ref(true)
const typeTabs = TYPE_TABS
const typeEnabled = reactive({ 1: true, 2: true, 3: true, 4: true })
const rulesByType = reactive({
  1: [{ remind_time: '08:00', sort_order: 0 }],
  2: [{ remind_time: '17:30', sort_order: 0 }],
  3: [{ remind_time: '08:00', sort_order: 0 }],
  4: [{ remind_time: '07:00', sort_order: 0 }],
})

const notificationSupported = computed(() => isNotificationSupported())
const browserNotifyLabel = computed(() => {
  if (!notificationSupported.value) return '不支持'
  const p = getNotificationPermission()
  if (p === 'granted') return '已授权'
  if (p === 'denied') return '已拒绝'
  return '未授权'
})
const browserBadgeClass = computed(() => {
  const label = browserNotifyLabel.value
  if (label === '已授权') return 'is-on'
  if (label === '已拒绝') return 'is-denied'
  return 'is-off'
})

onMounted(loadAll)

function normalizeRemindTime(value) {
  if (!value) return '08:00'
  const s = String(value)
  return s.length >= 5 ? s.slice(0, 5) : s
}

async function loadAll() {
  loading.value = true
  try {
    const [profile, rules] = await Promise.all([
      getUserProfile(),
      getReminderRules(),
    ])
    const ps = profile?.privacy_settings || profile?.privacySettings || {}
    checkinNotify.value = ps.checkin_notify ?? ps.checkinNotify ?? true

    if (rules.length) {
      applyRulesFromApi(rules)
    } else {
      await applyDefaults(false)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function applyRulesFromApi(rules) {
  TYPE_TABS.forEach((t) => {
    rulesByType[t.type] = []
    typeEnabled[t.type] = false
  })
  rules.forEach((r) => {
    const type = r.checkin_type
    if (!rulesByType[type]) return
    rulesByType[type].push({
      remind_time: normalizeRemindTime(r.remind_time),
      sort_order: r.sort_order ?? rulesByType[type].length,
      enabled: r.enabled !== false,
    })
    if (r.enabled !== false) typeEnabled[type] = true
  })
  TYPE_TABS.forEach((t) => {
    if (!rulesByType[t.type].length) {
      rulesByType[t.type] = [{ remind_time: '08:00', sort_order: 0 }]
    }
  })
}

async function applyDefaults(showMsg = true) {
  try {
    const defaults = await getReminderDefaults()
    TYPE_TABS.forEach((t) => {
      rulesByType[t.type] = []
      typeEnabled[t.type] = false
    })
    defaults.forEach((r) => {
      const type = r.checkin_type
      rulesByType[type].push({
        remind_time: normalizeRemindTime(r.remind_time),
        sort_order: r.sort_order ?? rulesByType[type].length,
      })
      typeEnabled[type] = true
    })
    if (showMsg) ElMessage.success('已加载推荐时段')
  } catch (e) {
    ElMessage.error(e.message || '加载推荐时段失败')
  }
}

function addSlot(type) {
  rulesByType[type].push({ remind_time: '12:00', sort_order: rulesByType[type].length })
}

function removeSlot(type, idx) {
  rulesByType[type].splice(idx, 1)
}

async function enableBrowserNotify() {
  const result = await requestNotificationPermission()
  if (result === 'granted') {
    ElMessage.success('浏览器通知已开启')
  } else if (result === 'denied') {
    ElMessage.warning('您已拒绝浏览器通知，请在浏览器设置中手动开启')
  } else if (result === 'unsupported') {
    ElMessage.warning('当前环境不支持浏览器通知')
  }
}

async function handleSave() {
  const payload = []
  TYPE_TABS.forEach((t) => {
    rulesByType[t.type].forEach((slot, idx) => {
      payload.push({
        checkin_type: t.type,
        remind_time: normalizeRemindTime(slot.remind_time),
        enabled: typeEnabled[t.type],
        sort_order: idx,
      })
    })
  })
  saving.value = true
  try {
    await saveReminderRules(payload)
    ElMessage.success('提醒设置已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.settings-page {
  max-width: 720px;
  margin: 0 auto;
  padding-bottom: 16px;
}

.intro-card {
  position: relative;
  margin-bottom: 12px;
  overflow: hidden;
}

.intro-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--health-400), var(--health-600));
}

.intro-text {
  margin: 0 0 14px;
  color: #64748b;
  line-height: 1.65;
  font-size: 14px;
}

.status-list {
  border-top: 1px solid #f5f5f4;
  padding-top: 4px;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
}

.status-row + .status-row {
  border-top: 1px solid #f5f5f4;
}

.status-label {
  font-size: 14px;
  font-weight: 500;
  color: #44403c;
  flex-shrink: 0;
}

.status-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
}

.status-badge.is-on {
  background: #ecfdf5;
  color: #059669;
}

.status-badge.is-off {
  background: #f5f5f4;
  color: #78716c;
}

.status-badge.is-denied {
  background: #fef2f2;
  color: #dc2626;
}

.text-link {
  background: none;
  border: none;
  color: var(--health-600);
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  padding: 0;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.text-link:hover {
  color: var(--health-700);
}

.hint-muted {
  margin: 8px 0 0;
  padding-top: 12px;
  border-top: 1px solid #f5f5f4;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}

.type-switcher-wrap {
  margin-bottom: 12px;
}

.type-switcher {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  padding-bottom: 2px;
}

.type-switcher::-webkit-scrollbar {
  display: none;
}

.type-switcher-btn {
  flex: 0 0 auto;
  padding: 9px 20px;
  border-radius: 999px;
  border: 1px solid #e7e5e4;
  background: #fff;
  font-size: 14px;
  font-weight: 600;
  color: #78716c;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.type-switcher-btn:hover:not(.active) {
  border-color: #d6d3d1;
  color: #57534e;
}

.type-switcher-btn.active {
  background: var(--health-600);
  border-color: var(--health-600);
  color: #fff;
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.25);
}

.type-panel {
  margin-bottom: 12px;
}

.type-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid #f5f5f4;
}

.type-head__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.type-head__title {
  font-weight: 600;
  font-size: 15px;
  color: #292524;
}

.type-head__desc {
  font-size: 12px;
  color: #a8a29e;
  line-height: 1.4;
}

.time-slot-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.time-slot-card {
  background: #fafaf9;
  border: 1px solid #f0eeec;
  border-radius: 14px;
  padding: 12px 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.time-slot-card:hover {
  border-color: #e7e5e4;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.time-slot-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.time-slot-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 8px;
  background: #f0fdfa;
  color: var(--health-600);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.time-slot-label {
  font-size: 13px;
  font-weight: 500;
  color: #78716c;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.time-picker {
  width: 148px;
  flex: 0 0 auto;
}

.time-picker :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e7e5e4 inset;
  background: #fff;
}

.time-picker :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--health-500) inset;
}

.delete-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #a8a29e;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.delete-btn:hover:not(:disabled) {
  background: #fef2f2;
  color: #ef4444;
}

.delete-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.add-slot-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 14px;
  border: 1px dashed #d6d3d1;
  border-radius: 10px;
  background: transparent;
  color: var(--health-600);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-slot-btn:hover {
  border-color: var(--health-500);
  background: #f0fdfa;
  color: var(--health-700);
}

.footer-spacer {
  height: 72px;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.footer-btn {
  min-width: 120px;
}

@media (max-width: 768px) {
  .settings-page {
    padding-bottom: 8px;
  }

  .intro-text {
    font-size: 13px;
  }

  .status-row {
    padding: 10px 0;
  }

  .status-label {
    font-size: 13px;
  }

  .status-actions {
    gap: 8px;
  }

  .type-switcher {
    margin: 0 -16px;
    padding: 0 16px 2px;
  }

  .type-switcher-btn {
    padding: 8px 16px;
    font-size: 13px;
  }

  .type-head__title {
    font-size: 14px;
  }

  .time-picker {
    flex: 1;
    width: auto;
    min-width: 0;
  }

  .footer-spacer {
    height: 80px;
  }

  .footer-actions {
    position: fixed;
    left: 0;
    right: 0;
    bottom: calc(64px + env(safe-area-inset-bottom));
    z-index: 50;
    display: flex;
    gap: 10px;
    padding: 10px 16px;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(12px);
    border-top: 1px solid rgba(231, 229, 228, 0.8);
    box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.06);
    margin-top: 0;
  }

  .footer-btn {
    flex: 1;
    min-width: 0;
    height: 44px;
    margin: 0;
  }

  .footer-btn--secondary {
    flex: 0 0 auto;
    min-width: 96px;
  }
}

@media (min-width: 769px) {
  .type-switcher-btn {
    flex: 1;
    text-align: center;
  }
}
</style>
