import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'DemoEntry',
    component: () => import('../views/DemoEntry.vue')
  },
  {
    path: '/diagnosis/:sessionUuid',
    name: 'Diagnosis',
    component: () => import('../views/Diagnosis.vue')
  },
  {
    path: '/guide/:slug',
    name: 'Guide',
    component: () => import('../views/Guide.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

export default router
