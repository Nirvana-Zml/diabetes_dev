<template>
  <div class="auth-page">
    <div class="auth-page__accent" aria-hidden="true" />

    <nav class="auth-nav">
      <router-link to="/login" class="auth-back">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        返回登录
      </router-link>
    </nav>

    <div class="auth-header auth-header--compact">
      <AuthLogo size="sm" />
      <h1 class="auth-title">重置密码</h1>
      <p class="auth-subtitle">通过注册邮箱验证身份后设置新密码</p>
    </div>

    <div class="auth-tip">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>验证码将发送至您注册时绑定的邮箱，请注意查收。</span>
    </div>

    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="邮箱" prop="account">
        <el-input
          v-model="form.account"
          placeholder="请输入注册邮箱"
          clearable
          inputmode="email"
          autocomplete="email"
          :prefix-icon="Message"
        />
      </el-form-item>

      <el-form-item label="验证码" prop="code">
        <div class="verify-row">
          <el-input
            v-model="form.code"
            placeholder="6 位验证码"
            maxlength="6"
            clearable
            inputmode="numeric"
            :prefix-icon="Key"
          />
          <el-button
            :disabled="countdown > 0"
            :loading="sendingCode"
            @click="handleSendCode"
          >
            {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
          </el-button>
        </div>
      </el-form-item>

      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          placeholder="6-32 个字符"
          show-password
          clearable
          :prefix-icon="Lock"
        />
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="再次输入新密码"
          show-password
          clearable
          :prefix-icon="Lock"
        />
      </el-form-item>

      <el-button type="primary" :loading="loading" native-type="submit">
        重置密码
      </el-button>
    </el-form>

    <div class="auth-footer">
      想起密码了？
      <router-link to="/login">返回登录</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Message, Key, Lock } from '@element-plus/icons-vue'
import AuthLogo from '@/components/AuthLogo.vue'
import { resetPassword, sendVerifyCode } from '@/api/auth.js'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  account: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const validateAccount = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入邮箱'))
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱'))
  } else {
    callback()
  }
}

const validateConfirm = (_rule, value, callback) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  account: [{ validator: validateAccount, trigger: 'blur' }],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

function startCountdown() {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
    }
  }, 1000)
}

async function handleSendCode() {
  const valid = await formRef.value?.validateField('account').catch(() => false)
  if (!valid) return

  sendingCode.value = true
  try {
    await sendVerifyCode({
      account: form.account.trim(),
      type: 'email',
      purpose: 'reset_password',
    })
    ElMessage.success('验证码已发送，请查收邮件')
    startCountdown()
  } catch (err) {
    ElMessage.error(err.message || '发送失败，请稍后重试')
  } finally {
    sendingCode.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await resetPassword({
      account: form.account.trim(),
      code: form.code,
      new_password: form.newPassword,
    })
    ElMessage.success('密码重置成功，请登录')
    router.push('/login')
  } catch (err) {
    ElMessage.error(err.message || '重置失败，请检查验证码')
  } finally {
    loading.value = false
  }
}
</script>
