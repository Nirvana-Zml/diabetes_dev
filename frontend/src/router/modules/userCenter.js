export default [
  {
    path: '/user-center',
    name: 'UserCenter',
    component: () => import('@/views/UserCenter/index.vue'),
    meta: { title: '个人中心' },
  },
  {
    path: '/profile',
    redirect: '/user-center',
  },
]
