<template>
  <el-dialog :model-value="modelValue" title="数据导出" width="92%" @update:model-value="$emit('update:modelValue', $event)">
    <p class="export-tip">选择需要导出的数据范围，系统将异步生成文件并通知您下载。</p>
    <el-form label-position="top">
      <el-form-item label="导出范围">
        <el-checkbox-group v-model="types">
          <el-checkbox value="health">健康档案</el-checkbox>
          <el-checkbox value="consultation">问诊记录</el-checkbox>
          <el-checkbox value="plan">健康方案</el-checkbox>
          <el-checkbox value="checkin">打卡记录</el-checkbox>
          <el-checkbox value="risk">风险评估</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="导出格式">
        <el-radio-group v-model="format">
          <el-radio value="pdf">PDF</el-radio>
          <el-radio value="excel">Excel</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="时间范围（选填）">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width:100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">提交导出</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { exportUserData } from '@/api/user'

defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'exported'])

const types = ref(['health'])
const format = ref('pdf')
const dateRange = ref([])
const loading = ref(false)

watch(
  () => types,
  () => {},
  { deep: true },
)

async function submit() {
  if (!types.value.length) {
    ElMessage.warning('请至少选择一项导出范围')
    return
  }
  loading.value = true
  try {
    const res = await exportUserData({
      types: types.value,
      format: format.value,
      start_date: dateRange.value?.[0],
      end_date: dateRange.value?.[1],
    })
    emit('exported', res)
    emit('update:modelValue', false)
    ElMessage.success(res.message || '导出任务已提交')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.export-tip {
  font-size: 13px;
  color: #909399;
  margin: 0 0 16px;
  line-height: 1.6;
}
</style>
