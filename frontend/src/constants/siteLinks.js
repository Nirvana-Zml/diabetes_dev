import { APP_NAME } from '@/config'

export const PLATFORM_SERVICE_LINKS = [
  { key: 'eval', label: '风险评估', path: '/health-evaluation', requireAuth: true },
  { key: 'plan', label: '健康方案', path: '/living-plans', requireAuth: true },
  { key: 'consult', label: '医师咨询', path: '/consultation', requireAuth: true },
  { key: 'info', label: '知识科普', path: '/health-info' },
]

export const PLATFORM_ABOUT_LINKS = [
  { key: 'intro', label: '平台介绍', path: '/health-info' },
  { key: 'doctor', label: '医师入驻', path: '/consultation', requireAuth: true },
  { key: 'terms', label: '用户协议', path: '/login' },
  { key: 'privacy', label: '隐私政策', path: '/login' },
]

export const PLATFORM_CONTACT = {
  email: 'support@diabetes-helper.com',
  phone: '400-888-8888',
}

export const PLATFORM_EXTRA_LINKS = [
  { key: 'terms', label: '用户协议', path: '/login' },
  { key: 'privacy', label: '隐私政策', path: '/login' },
  { key: 'help', label: '帮助中心', path: '/assistant' },
]

export const PLATFORM_COPYRIGHT = `© 2026 ${APP_NAME} · 保留所有权利`

export const PLATFORM_FOOTER_TIP = '本系统内容仅供参考，不构成诊疗建议'
