const CHECKIN_TABS = ['food', 'medication', 'exercise', 'glucose']

function redirectLegacyTab(to) {
  const tab = to.query.tab
  if (tab && CHECKIN_TABS.includes(tab)) {
    return { path: `/checkin-records/${tab}`, replace: true }
  }
  return true
}

export default [
  {
    path: '/checkin-records',
    name: 'CheckinRecords',
    component: () => import('@/views/CheckinRecords/index.vue'),
    beforeEnter: redirectLegacyTab,
    meta: {
      title: '生活打卡',
      hideBottomNav: true,
    },
  },
  {
    path: '/checkin-records/food',
    name: 'FoodCheckin',
    component: () => import('@/views/CheckinRecords/FoodCheckin.vue'),
    meta: {
      title: '食物打卡',
      hideBottomNav: true,
    },
  },
  {
    path: '/checkin-records/medication',
    name: 'MedicationCheckin',
    component: () => import('@/views/CheckinRecords/MedicationCheckin.vue'),
    meta: {
      title: '用药打卡',
      hideBottomNav: true,
    },
  },
  {
    path: '/checkin-records/exercise',
    name: 'ExerciseCheckin',
    component: () => import('@/views/CheckinRecords/ExerciseCheckin.vue'),
    meta: {
      title: '运动打卡',
      hideBottomNav: true,
    },
  },
  {
    path: '/checkin-records/glucose',
    name: 'GlucoseCheckin',
    component: () => import('@/views/CheckinRecords/GlucoseCheckin.vue'),
    meta: {
      title: '血糖打卡',
      hideBottomNav: true,
    },
  },
  {
    path: '/checkin-records/achievements',
    name: 'AchievementWall',
    component: () => import('@/views/CheckinRecords/AchievementWall.vue'),
    meta: {
      title: '成就墙',
      hideBottomNav: true,
    },
  },
]
