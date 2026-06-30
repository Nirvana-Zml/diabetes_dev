export default [
  {
    path: '/science-videos',
    name: 'ScienceVideos',
    component: () => import('@/views/ScienceVideos/index.vue'),
    meta: { title: '科普视频', public: true },
  },
]
