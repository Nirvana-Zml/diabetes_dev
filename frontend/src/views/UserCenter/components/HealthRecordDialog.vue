<template>
  <el-dialog :model-value="modelValue" title="编辑健康档案" width="92%" top="4vh" @update:model-value="$emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="身高 (cm)" prop="height">
            <el-input-number v-model="form.height" :min="100" :max="250" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="体重 (kg)" prop="weight">
            <el-input-number v-model="form.weight" :min="30" :max="300" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="空腹血糖 (mmol/L)" prop="fasting_glucose">
            <el-input-number v-model="form.fasting_glucose" :min="2" :max="30" :step="0.1" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="餐后2h血糖 (mmol/L)">
            <el-input-number v-model="form.postprandial_glucose" :min="2" :max="30" :step="0.1" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="糖化血红蛋白 (%)">
            <el-input-number v-model="form.hba1c" :min="4" :max="15" :step="0.1" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="糖尿病类型">
            <el-select v-model="form.diabetes_type" style="width:100%">
              <el-option v-for="opt in DIABETES_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="收缩压 (mmHg)" prop="systolic_bp">
            <el-input-number v-model="form.systolic_bp" :min="60" :max="250" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="舒张压 (mmHg)" prop="diastolic_bp">
            <el-input-number v-model="form.diastolic_bp" :min="30" :max="150" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="吸烟状况">
            <el-select v-model="form.smoking" style="width:100%">
              <el-option v-for="opt in SMOKING_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="运动频率">
            <el-select v-model="form.exercise_freq" style="width:100%">
              <el-option v-for="opt in EXERCISE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="饮食偏好">
            <el-select v-model="form.diet_type" style="width:100%">
              <el-option v-for="opt in DIET_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="糖尿病家族史">
            <el-radio-group v-model="form.family_history">
              <el-radio :value="true">有</el-radio>
              <el-radio :value="false">无</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="既往病史">
            <el-input v-model="form.medical_history" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="当前用药">
            <el-input v-model="form.medication" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateHealthRecord } from '@/api/user'
import {
  DIABETES_TYPE_OPTIONS,
  SMOKING_OPTIONS,
  EXERCISE_OPTIONS,
  DIET_OPTIONS,
} from '../constants'

const props = defineProps({
  modelValue: Boolean,
  record: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref()
const loading = ref(false)
const form = ref({})

const rules = {
  height: [{ required: true, message: '请输入身高', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入体重', trigger: 'blur' }],
  fasting_glucose: [{ required: true, message: '请输入空腹血糖', trigger: 'blur' }],
  systolic_bp: [{ required: true, message: '请输入收缩压', trigger: 'blur' }],
  diastolic_bp: [{ required: true, message: '请输入舒张压', trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) form.value = { ...props.record }
  },
)

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const fg = form.value.fasting_glucose
  if (fg < 2 || fg > 30) {
    ElMessage.error('空腹血糖应在 2.0~30.0 mmol/L 范围内')
    return
  }
  if (fg > 15 || fg < 3) {
    try {
      await ElMessageBox.confirm(`血糖值 ${fg} mmol/L 异常，确认保存？`, '数值确认')
    } catch {
      return
    }
  }

  loading.value = true
  try {
    const data = await updateHealthRecord({ ...form.value })
    emit('saved', data)
    emit('update:modelValue', false)
    ElMessage.success('健康档案已更新')
  } finally {
    loading.value = false
  }
}
</script>
