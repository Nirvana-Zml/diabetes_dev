export default [
  {
    path: '/health-evaluation',
    name: 'HealthEvaluation',
    component: () => import('@/views/HealthEvaluation/index.vue'),
    meta: {
      title: '健康评估'
    }
  }
]