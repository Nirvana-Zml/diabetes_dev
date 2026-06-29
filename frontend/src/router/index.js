import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, isPublicRoute, redirectToLogin } from '@/utils/auth'

const routeFiles = import.meta.glob('./modules/*.js', { eager: true })
let asyncRoutes = []
Object.values(routeFiles).forEach((module) => {
  asyncRoutes.push(...module.default)
})

const constantRoutes = [
  { path: '/', redirect: '/home' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login/index.vue'),
    meta: { title: '登录', layout: 'auth', public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register/index.vue'),
    meta: { title: '注册', layout: 'auth', public: true },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPassword/index.vue'),
    meta: { title: '忘记密码', layout: 'auth', public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: [...constantRoutes, ...asyncRoutes],
})

router.beforeEach((to) => {
  if (isPublicRoute(to) || to.path === '/login') return true
  if (isLoggedIn()) return true
  return redirectToLogin(to.fullPath)
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '糖尿病预治智能助手'} - 糖尿病预治智能助手`
})

export default router
