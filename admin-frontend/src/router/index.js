import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, isAdmin, redirectToLogin } from '@/utils/auth'
import { APP_NAME } from '@/config'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login/index.vue'),
      meta: { title: '管理员登录', layout: 'auth', public: true },
    },
    {
      path: '/home',
      name: 'AdminHome',
      component: () => import('@/views/Home/index.vue'),
      meta: { title: '管理后台' },
    },
    {
      path: '/articles',
      name: 'AdminArticles',
      component: () => import('@/views/Articles/index.vue'),
      meta: { title: '资讯管理' },
    },
    {
      path: '/videos',
      name: 'AdminVideos',
      component: () => import('@/views/Videos/index.vue'),
      meta: { title: '科普视频管理' },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!isLoggedIn()) return redirectToLogin(to.fullPath)
  if (!isAdmin()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '管理后台'} - ${APP_NAME}`
})

export default router
