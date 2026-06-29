export default [
  {
    path: '/checkin-records',
    name: 'CheckinRecords',
    component: () => import('@/views/CheckinRecords/index.vue'),
    meta: {
      title: '打卡记录'
    }
  }
]