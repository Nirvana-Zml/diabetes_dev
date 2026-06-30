<template>
  <el-dialog
    :model-value="modelValue"
    class="security-dialog"
    title="账户安全"
    width="440px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
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
            <el-input :model-value="profile.phone || '未绑定'" disabled />
          </el-form-item>
          <el-form-item label="新手机号">
            <el-input v-model="phoneForm.phone" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="code-row">
              <el-input v-model="phoneForm.code" placeholder="6 位验证码" maxlength="6" />
              <el-button :disabled="phoneCodeCooldown > 0" @click="sendPhoneCode">
                {{ phoneCodeCooldown > 0 ? `${phoneCodeCooldown}s 后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-button type="primary" :loading="phoneLoading" @click="bindPhoneSubmit">确认绑定</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="绑定邮箱" name="email">
        <el-form ref="emailRef" :model="emailForm" :rules="emailRules" label-position="top">
          <el-form-item label="当前邮箱">
            <el-input :model-value="profile.email || '未绑定'" disabled />
          </el-form-item>
          <el-form-item label="新邮箱" prop="email">
            <el-input v-model="emailForm.email" placeholder="请输入新邮箱" maxlength="100" />
          </el-form-item>
          <el-form-item label="验证码" prop="code">
            <div class="code-row">
              <el-input v-model="emailForm.code" placeholder="6 位验证码" maxlength="6" />
              <el-button :disabled="emailCodeCooldown > 0" @click="sendEmailCode">
                {{ emailCodeCooldown > 0 ? `${emailCodeCooldown}s 后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-button type="primary" :loading="emailLoading" @click="bindEmailSubmit">确认绑定</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword, bindEmail as bindEmailApi, bindPhone as bindPhoneApi } from '@/api/user'
import { sendVerifyCode } from '@/api/auth'

defineProps({
  modelValue: Boolean,
  profile: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const tab = ref('password')
const pwdRef = ref()
const emailRef = ref()
const loading = ref(false)
const phoneLoading = ref(false)
const emailLoading = ref(false)
const phoneCodeCooldown = ref(0)
const emailCodeCooldown = ref(0)
const pwdForm = reactive({ old_password: '', new_password: '', confirm_password: '' })
const phoneForm = reactive({ phone: '', code: '' })
const emailForm = reactive({ email: '', code: '' })

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const validateEmail = (_r, v, cb) => {
  if (!v) cb(new Error('请输入邮箱'))
  else if (!EMAIL_PATTERN.test(v)) cb(new Error('邮箱格式不正确'))
  else cb()
}

const emailRules = {
  email: [{ validator: validateEmail, trigger: 'blur' }],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位', trigger: 'blur' },
  ],
}

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
    ElMessage.success('密码修改成功')
    emit('update:modelValue', false)
  } finally {
    loading.value = false
  }
}

function startCooldown(target) {
  target.value = 60
  const timer = setInterval(() => {
    target.value -= 1
    if (target.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendPhoneCode() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  try {
    await sendVerifyCode({ account: phoneForm.phone, type: 'phone', purpose: 'bind' })
    ElMessage.success('验证码已发送')
    startCooldown(phoneCodeCooldown)
  } catch (err) {
    ElMessage.error(err.message || '发送失败，请稍后重试')
  }
}

async function sendEmailCode() {
  const valid = await emailRef.value?.validateField('email').catch(() => false)
  if (!valid) return
  try {
    await sendVerifyCode({ account: emailForm.email, type: 'email', purpose: 'bind' })
    ElMessage.success('验证码已发送，请查收邮件')
    startCooldown(emailCodeCooldown)
  } catch (err) {
    ElMessage.error(err.message || '发送失败，请稍后重试')
  }
}

async function bindPhoneSubmit() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!/^\d{6}$/.test(phoneForm.code)) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  phoneLoading.value = true
  try {
    const data = await bindPhoneApi({ phone: phoneForm.phone, verify_code: phoneForm.code })
    ElMessage.success('手机绑定成功')
    emit('saved', data)
    emit('update:modelValue', false)
  } catch (err) {
    ElMessage.error(err.message || '手机绑定失败')
  } finally {
    phoneLoading.value = false
  }
}

async function bindEmailSubmit() {
  const valid = await emailRef.value?.validate().catch(() => false)
  if (!valid) return
  emailLoading.value = true
  try {
    const data = await bindEmailApi({ email: emailForm.email, verify_code: emailForm.code })
    ElMessage.success('邮箱绑定成功')
    emit('saved', data)
    emit('update:modelValue', false)
  } catch (err) {
    ElMessage.error(err.message || '邮箱绑定失败')
  } finally {
    emailLoading.value = false
  }
}
</script>

<style scoped>
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row .el-input { flex: 1; }
</style>

<style>
@media (max-width: 480px) {
  .security-dialog.el-dialog {
    width: 92% !important;
    max-width: 92%;
  }
}
</style>
