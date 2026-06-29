export default [
  {
    path: '/consultation',
    name: 'Consultation',
    component: () => import('@/views/Consultation/index.vue'),
    meta: { title: '医师咨询' },
  },
  {
    path: '/consultation/chat',
    name: 'ConsultationChat',
    component: () => import('@/views/Consultation/index.vue'),
    meta: { title: '在线咨询' },
  },
]
