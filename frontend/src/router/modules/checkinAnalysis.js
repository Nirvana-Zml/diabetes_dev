export default [
  {
    path: '/checkin-analysis',
    name: 'CheckinAnalysis',
    component: () => import('@/views/CheckinAnalysis/index.vue'),
    meta: {
      title: '打卡分析'
    }
  }
]