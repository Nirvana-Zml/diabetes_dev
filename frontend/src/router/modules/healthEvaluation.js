export default [
  {
    path: '/health-evaluation',
    name: 'HealthEvaluation',
    component: () => import('@/views/HealthEvaluation/index.vue'),
    meta: {
      title: '风险评估',
    },
  },
]
