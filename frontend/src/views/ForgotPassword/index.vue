<template>
  <div class="auth-page">
    <router-link to="/login" class="auth-back">← 返回登录</router-link>

    <div class="auth-tip">
      验证码将发送至您注册时绑定的手机号或邮箱。若未绑定，请联系管理员处理。
    </div>

    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="验证方式" prop="verifyType">
        <el-radio-group v-model="form.verifyType" @change="onVerifyTypeChange">
          <el-radio value="phone">手机号</el-radio>
          <el-radio value="email">邮箱</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item
        :label="form.verifyType === 'phone' ? '手机号' : '邮箱'"
        prop="account"
      >
        <el-input
          v-model="form.account"
          :placeholder="form.verifyType === 'phone' ? '请输入注册手机号' : '请输入注册邮箱'"
          clearable
        />
      </el-form-item>

      <el-form-item label="验证码" prop="code">
        <div class="verify-row">
          <el-input
            v-model="form.code"
            placeholder="6 位验证码"
            maxlength="6"
            clearable
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
        />
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="再次输入新密码"
          show-password
          clearable
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
import { resetPassword, sendVerifyCode } from '@/api/auth.js'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  verifyType: 'phone',
  account: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const validateAccount = (_rule, value, callback) => {
  if (!value) {
    callback(new Error(form.verifyType === 'phone' ? '请输入手机号' : '请输入邮箱'))
    return
  }
  if (form.verifyType === 'phone' && !/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else if (form.verifyType === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
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

function onVerifyTypeChange() {
  form.account = ''
  formRef.value?.clearValidate('account')
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
      type: form.verifyType,
    })
    ElMessage.success('验证码已发送')
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
