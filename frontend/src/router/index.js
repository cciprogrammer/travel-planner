import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  {
    path: '/',
    component: () => import('@/views/MainLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { requireAuth: true } },
      { path: 'plans', name: 'plans', component: () => import('@/views/PlansView.vue'), meta: { requireAuth: true } },
      { path: 'plans/:id', name: 'plan-detail', component: () => import('@/views/PlanDetailView.vue'), meta: { requireAuth: true } },
      { path: 'favorites', name: 'favorites', component: () => import('@/views/FavoritesView.vue'), meta: { requireAuth: true } },
      { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { requireAuth: true } },
      { path: 'admin', name: 'admin', component: () => import('@/views/AdminView.vue'), meta: { requireAuth: true, requireAdmin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.meta.requireAuth && !store.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.meta.requireAdmin && !store.isAdmin) {
    ElMessage.warning('该页面仅管理员可访问')
    next('/home')
  } else if (to.path === '/login' && store.isLogin) {
    next('/home')
  } else {
    next()
  }
})

export default router
