<template>
  <div class="auth-page">
    <div class="auth-header">
      <div class="auth-logo" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 21s-6-4.35-6-10a6 6 0 0 1 12 0c0 5.65-6 10-6 10z" />
          <path d="M12 11v4M12 9h.01" />
        </svg>
      </div>
      <h1 class="auth-title">糖尿病预治助手</h1>
      <p class="auth-subtitle">登录账号，开启智能健康管理</p>
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
          placeholder="请输入用户名"
          maxlength="50"
          clearable
          :prefix-icon="User"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          show-password
          clearable
          :prefix-icon="Lock"
          @keyup.enter="handleSubmit"
        />
      </el-form-item>

      <div class="auth-links">
        <el-checkbox v-model="form.remember">记住我</el-checkbox>
        <router-link to="/forgot-password">忘记密码？</router-link>
      </div>

      <el-button type="primary" :loading="loading" native-type="submit">
        登 录
      </el-button>
    </el-form>

    <div class="auth-footer">
      还没有账号？
      <router-link to="/register">立即注册</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, saveTokens, clearTokens } from '@/api/auth.js'
import { ADMIN_PORTAL_URL } from '@/config'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  remember: false,
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 个字符', trigger: 'blur' },
  ],
}

onMounted(() => {
  const saved = localStorage.getItem('remember_username')
  if (saved) {
    form.username = saved
    form.remember = true
  }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({
      username: form.username.trim(),
      password: form.password,
    })
    const role = data.role || data.user?.role || 'user'
    if (role === 'admin') {
      clearTokens()
      ElMessage.warning('管理员请使用管理后台登录')
      window.location.href = ADMIN_PORTAL_URL
      return
    }
    saveTokens(data)
    if (form.remember) {
      localStorage.setItem('remember_username', form.username.trim())
    } else {
      localStorage.removeItem('remember_username')
    }
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.push(redirect)
  } catch (err) {
    ElMessage.error(err.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>
