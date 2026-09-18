import { createRouter, createWebHistory } from 'vue-router'
import api from '../api'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue')
      },
      {
        path: 'shops',
        name: 'Shops',
        component: () => import('../views/Shops.vue')
      },
      {
        path: 'products',
        name: 'Products',
        component: () => import('../views/Products.vue')
      },
      {
        path: 'manuals',
        name: 'Manuals',
        component: () => import('../views/Manuals.vue')
      },
      {
        path: 'sessions',
        name: 'Sessions',
        component: () => import('../views/Sessions.vue')
      },
      {
        path: 'sessions/:id',
        name: 'SessionDetail',
        component: () => import('../views/SessionDetail.vue')
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('../views/Knowledge.vue')
      },
      {
        path: 'ai-usage',
        name: 'AIUsage',
        component: () => import('../views/AIUsage.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 登录守卫：本地标记 + 后端会话双重校验（后端拦截器才是真正的门）
router.beforeEach(async (to) => {
  if (to.meta.public) return true

  const localFlag = localStorage.getItem('toolfix_admin')
  if (!localFlag) return { path: '/login' }

  // 后端会话可能已过期（后端重启等），静默探测一次
  const res = await api.auth.me()
  if (res.backendDown) return true // GitHub Pages 纯前端演示模式，放行
  if (!res.success || !res.data?.email) {
    localStorage.removeItem('toolfix_admin')
    return { path: '/login' }
  }
  return true
})

export default router
