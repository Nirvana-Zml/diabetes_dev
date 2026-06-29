export default [
  {
    path: '/assistant',
    name: 'Assistant',
    component: () => import('@/views/Assistant/index.vue'),
    meta: {
      title: 'AI 助手',
      public: true,
    }
  }
]