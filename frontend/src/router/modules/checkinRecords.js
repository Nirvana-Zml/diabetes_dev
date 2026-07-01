export default [
  {
    path: '/checkin-records',
    name: 'CheckinRecords',
    component: () => import('@/views/CheckinRecords/index.vue'),
    meta: {
      title: '生活打卡',
      hideBottomNav: true,
    }
  }
]