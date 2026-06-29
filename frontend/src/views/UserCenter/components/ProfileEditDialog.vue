<template>
  <el-dialog :model-value="modelValue" title="编辑个人信息" width="92%" @update:model-value="$emit('update:modelValue', $event)">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="avatar-row">
        <el-avatar :size="72" :src="form.avatar_url" />
        <el-button size="small" :loading="uploading" @click="handleAvatarUpload">更换头像</el-button>
        <input ref="fileRef" type="file" accept="image/*" hidden @change="onFileChange" />
      </div>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="form.nickname" maxlength="50" />
      </el-form-item>
      <el-form-item label="性别" prop="gender">
        <el-radio-group v-model="form.gender">
          <el-radio value="male">男</el-radio>
          <el-radio value="female">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="出生日期" prop="birth_date">
        <el-date-picker v-model="form.birth_date" type="date" value-format="YYYY-MM-DD" style="width:100%" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="form.phone" disabled placeholder="请前往账户安全绑定" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" disabled placeholder="请前往账户安全绑定" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { updateUserProfile, uploadUserAvatar } from '@/api/user'

const props = defineProps({
  modelValue: Boolean,
  profile: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref()
const fileRef = ref()
const loading = ref(false)
const uploading = ref(false)
const form = ref({})

const rules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) form.value = { ...props.profile }
  },
)

function handleAvatarUpload() {
  fileRef.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 2MB')
    return
  }
  uploading.value = true
  try {
    const data = await uploadUserAvatar(file)
    form.value.avatar_url = data.avatar_url
    ElMessage.success('头像已上传')
  } catch (err) {
    ElMessage.error(err?.message || '头像上传失败')
  } finally {
    uploading.value = false
    if (fileRef.value) fileRef.value.value = ''
  }
}

async function submit() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = await updateUserProfile({
      nickname: form.value.nickname,
      gender: form.value.gender,
      birth_date: form.value.birth_date,
    })
    emit('saved', data)
    emit('update:modelValue', false)
    ElMessage.success('个人信息已更新')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
</style>
