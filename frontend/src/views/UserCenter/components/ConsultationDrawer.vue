<template>
  <el-drawer :model-value="modelValue" title="就诊记录" size="90%" @update:model-value="$emit('update:modelValue', $event)">
    <div v-loading="loading">
      <div v-if="!list.length" class="empty">暂无就诊记录</div>
      <div v-for="item in list" :key="item.session_id" class="record-card">
        <div class="record-header">
          <div>
            <span class="doctor">{{ item.doctor_name }}</span>
            <el-tag size="small" type="info">{{ item.doctor_title }}</el-tag>
          </div>
          <el-tag :type="item.status === 'active' ? 'success' : 'info'" size="small">
            {{ CONSULT_STATUS_LABELS[item.status] }}
          </el-tag>
        </div>
        <div class="record-meta">{{ item.department }} · {{ formatTime(item.started_at) }}</div>
        <div v-if="item.rating" class="record-rating">
          <el-rate :model-value="item.rating" disabled />
          <span v-if="item.feedback" class="feedback">{{ item.feedback }}</span>
        </div>
        <el-button
          v-if="item.status === 'active'"
          type="primary"
          size="small"
          link
          @click="$router.push({ path: '/consultation/chat', query: { session_id: item.session_id } })"
        >
          继续咨询
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import dayjs from 'dayjs'
import { getUserConsultations } from '@/api/user'
import { CONSULT_STATUS_LABELS } from '../constants'

const props = defineProps({ modelValue: Boolean })
defineEmits(['update:modelValue'])

const loading = ref(false)
const list = ref([])

watch(
  () => props.modelValue,
  async (v) => {
    if (!v) return
    loading.value = true
    try {
      const data = await getUserConsultations()
      list.value = data.list
    } finally {
      loading.value = false
    }
  },
)

function formatTime(t) {
  return dayjs(t).format('YYYY-MM-DD HH:mm')
}
</script>

<style scoped>
.empty { text-align: center; color: #909399; padding: 40px 0; }
.record-card {
  background: #f5f7fa;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 12px;
}
.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.doctor { font-weight: 600; margin-right: 8px; }
.record-meta { font-size: 13px; color: #909399; }
.record-rating { margin-top: 8px; }
.feedback { font-size: 13px; color: #606266; margin-left: 8px; }
</style>
