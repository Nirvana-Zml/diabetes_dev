<template>
  <el-dialog :model-value="modelValue" title="账户安全" width="92%" @update:model-value="$emit('update:modelValue', $event)">
    <el-tabs v-model="tab">
      <el-tab-pane label="修改密码" name="password">
        <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-position="top">
          <el-form-item label="当前密码" prop="old_password">
            <el-input v-model="pwdForm.old_password" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码" prop="new_password">
            <el-input v-model="pwdForm.new_password" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirm_password">
            <el-input v-model="pwdForm.confirm_password" type="password" show-password />
          </el-form-item>
          <el-button type="primary" :loading="loading" @click="submitPassword">确认修改</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="绑定手机" name="phone">
        <el-form label-position="top">
          <el-form-item label="当前手机号">
            <el-input :model-value="profile.phone" disabled />
          </el-form-item>
          <el-form-item label="新手机号">
            <el-input v-model="phoneForm.phone" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="phoneForm.code" placeholder="6 位验证码" maxlength="6" />
              <el-button @click="sendCode">获取验证码</el-button>
            </div>
          </el-form-item>
          <el-button type="primary" @click="bindPhone">确认绑定</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/user'
import { sendVerifyCode } from '@/api/auth'

defineProps({
  modelValue: Boolean,
  profile: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue'])

const tab = ref('password')
const pwdRef = ref()
const loading = ref(false)
const pwdForm = reactive({ old_password: '', new_password: '', confirm_password: '' })
const phoneForm = reactive({ phone: '', code: '' })

const validateConfirm = (_r, v, cb) => {
  if (v !== pwdForm.new_password) cb(new Error('两次密码不一致'))
  else cb()
}

const pwdRules = {
  old_password: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  new_password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' },
  ],
  confirm_password: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function submitPassword() {
  const valid = await pwdRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await changePassword({
      old_password: pwdForm.old_password,
      new_password: pwdForm.new_password,
    })
    ElMessage.success('密码修改成功（占位）')
    emit('update:modelValue', false)
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  await sendVerifyCode({ account: phoneForm.phone, type: 'phone' })
  ElMessage.success('验证码已发送（占位）')
}

function bindPhone() {
  if (!phoneForm.phone || !phoneForm.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  ElMessage.success('手机绑定成功（占位）')
  emit('update:modelValue', false)
}
</script>

<style scoped>
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row .el-input { flex: 1; }
</style>
