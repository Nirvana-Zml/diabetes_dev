<template>
  <div class="auth-page">
    <div class="auth-header">
      <div class="auth-logo" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <path d="M9 12l2 2 4-4" />
        </svg>
      </div>
      <h1 class="auth-title">管理后台</h1>
      <p class="auth-subtitle">{{ APP_NAME }} · 内容运营平台</p>
    </div>

    <div class="auth-tip">
      请使用管理员账号登录。默认账号：<strong>admin</strong> / <strong>123456</strong>
    </div>

    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="管理员账号" prop="username">
        <el-input
          v-model="form.username"
          placeholder="请输入管理员账号"
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

      <el-button type="primary" :loading="loading" native-type="submit">
        登 录
      </el-button>
    </el-form>

    <div class="auth-footer">
      <a :href="userPortalUrl" target="_blank" rel="noopener">返回用户端</a>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, saveTokens, clearTokens } from '@/api/auth'
import { USER_PORTAL_URL, APP_NAME } from '@/config'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const userPortalUrl = USER_PORTAL_URL

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({
      username: form.username.trim(),
      password: form.password,
    })
    const role = data.role || data.user?.role
    if (role !== 'admin') {
      clearTokens()
      ElMessage.error('该账号无管理权限，请使用管理员账号登录')
      return
    }
    saveTokens(data)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.push(redirect)
  } catch (err) {
    ElMessage.error(err.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>
