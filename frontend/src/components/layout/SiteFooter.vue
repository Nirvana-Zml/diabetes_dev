<template>
  <footer class="site-footer">
    <div class="footer-inner">
      <div class="footer-grid">
        <div class="footer-col footer-col--brand">
          <div class="footer-brand">
            <span class="footer-logo">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
              </svg>
            </span>
            <span class="footer-name">糖尿病智能助手</span>
          </div>
          <p class="footer-desc">为糖尿病患者及关注人群提供一站式健康管理服务，让控糖更简单、更科学。</p>
        </div>
        <div class="footer-col">
          <h4 class="footer-heading">服务项目</h4>
          <ul class="footer-links">
            <li><button type="button" @click="navigateTo('/health-evaluation', true)">风险评估</button></li>
            <li><button type="button" @click="navigateTo('/living-plans', true)">健康方案</button></li>
            <li><button type="button" @click="navigateTo('/consultation', true)">医师咨询</button></li>
            <li><button type="button" @click="router.push('/health-info')">知识科普</button></li>
          </ul>
        </div>
        <div class="footer-col">
          <h4 class="footer-heading">关于我们</h4>
          <ul class="footer-links">
            <li><button type="button" @click="router.push('/health-info')">平台介绍</button></li>
            <li><button type="button" @click="navigateTo('/consultation', true)">医师入驻</button></li>
            <li><button type="button" @click="router.push('/login')">用户协议</button></li>
            <li><button type="button" @click="router.push('/login')">隐私政策</button></li>
          </ul>
        </div>
        <div class="footer-col">
          <h4 class="footer-heading">联系我们</h4>
          <ul class="footer-contact">
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
              </svg>
              support@diabetes-helper.com
            </li>
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 6.75c0 8.284 6.716 15 15 15h2.25a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106c-.44-.11-.902.055-1.173.417l-.97 1.293c-.282.376-.769.542-1.21.38a12.035 12.035 0 01-7.143-7.143c-.162-.441.004-.928.38-1.21l1.293-.97c.363-.271.527-.734.417-1.173L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z" />
              </svg>
              400-888-8888
            </li>
          </ul>
        </div>
      </div>
      <div class="footer-bottom">
        <p class="footer-copy">© 2026 糖尿病智能助手 · 保留所有权利</p>
        <div class="footer-bottom-links">
          <button type="button" @click="router.push('/login')">用户协议</button>
          <button type="button" @click="router.push('/login')">隐私政策</button>
          <button type="button" @click="router.push('/assistant')">帮助中心</button>
        </div>
      </div>
      <p class="footer-tip">本系统内容仅供参考，不构成诊疗建议</p>
    </div>
  </footer>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'

const router = useRouter()

function navigateTo(path, requireAuth = false) {
  if (requireAuth && !isLoggedIn()) {
    router.push(redirectToLogin(path))
    return
  }
  router.push(path)
}
</script>
