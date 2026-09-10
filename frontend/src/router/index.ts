import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/records',
      children: [
        { path: 'records', name: 'records', component: () => import('@/views/RecordListView.vue'), meta: { title: '采样记录' } },
        { path: 'records/:id', name: 'record-detail', component: () => import('@/views/RecordDetailView.vue'), meta: { title: '记录详情与审计时间线' } },
        { path: 'anomalies', name: 'anomalies', component: () => import('@/views/AnomalyListView.vue'), meta: { title: '异常处理' } },
        { path: 'imports', name: 'imports', component: () => import('@/views/ImportListView.vue'), meta: { title: '批量导入' } },
        { path: 'imports/:id', name: 'import-detail', component: () => import('@/views/ImportDetailView.vue'), meta: { title: '导入结果' } },
        { path: 'trend', name: 'trend', component: () => import('@/views/TrendView.vue'), meta: { title: '分贝趋势' } },
        { path: 'sensors', name: 'sensors', component: () => import('@/views/SensorListView.vue'), meta: { title: '传感器与批次' } },
        { path: 'audit', name: 'audit', component: () => import('@/views/AuditLogView.vue'), meta: { title: '审计日志' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/records' }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && auth.isLoggedIn) {
    return { path: '/records' }
  }
  return true
})

export default router
