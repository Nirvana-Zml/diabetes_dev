<template>
  <div class="auth-page">
    <router-link to="/login" class="auth-back">← 返回登录</router-link>

    <div class="auth-header">
      <AuthLogo />
      <h1 class="auth-title">创建账号</h1>
      <p class="auth-subtitle">注册后即可使用风险评估、打卡与 AI 问诊</p>
    </div>

    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          placeholder="3-50 个字符，用于登录"
          maxlength="50"
          clearable
        />
      </el-form-item>

      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="form.phone"
          placeholder="11 位手机号"
          maxlength="11"
          clearable
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="6-32 个字符"
          show-password
          clearable
        />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="再次输入密码"
          show-password
          clearable
          @keyup.enter="handleSubmit"
        />
      </el-form-item>

      <el-button type="primary" :loading="loading" native-type="submit">
        注 册
      </el-button>
    </el-form>

    <div class="auth-footer">
      已有账号？
      <router-link to="/login">去登录</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLogo from '@/components/AuthLogo.vue'
import { register } from '@/api/auth.js'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

const validateConfirm = (_rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' },
    { pattern: /^[\w\u4e00-\u9fa5-]+$/, message: '仅支持字母、数字、下划线及中文', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register({
      username: form.username.trim(),
      phone: form.phone,
      password: form.password,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (err) {
    ElMessage.error(err.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>
