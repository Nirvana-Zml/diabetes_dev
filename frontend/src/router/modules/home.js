export default [
  {
  path: '/home',
  name: 'Home',
  component: () => import('@/views/Home/index.vue'),
  meta: {
    title: '首页',
    public: true,
  }
  }
]