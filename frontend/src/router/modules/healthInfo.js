export default [
  {
    path: '/health-info',
    name: 'HealthInfo',
    component: () => import('@/views/HealthInfo/index.vue'),
    meta: { title: '健康资讯', public: true },
  },
  {
    path: '/health-info/:id',
    name: 'HealthInfoDetail',
    component: () => import('@/views/HealthInfo/index.vue'),
    meta: { title: '资讯详情', public: true },
  },
]
