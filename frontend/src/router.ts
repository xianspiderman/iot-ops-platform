import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', component: () => import('./views/DashboardView.vue') },
    { path: '/devices', component: () => import('./views/DevicesView.vue') },
    { path: '/device-imports', component: () => import('./views/DeviceImportsView.vue') },
    { path: '/work-orders', component: () => import('./views/WorkOrdersView.vue') },
    { path: '/reliability', component: () => import('./views/ReliabilityView.vue') },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.public && !localStorage.getItem('iot-ops-token')) return '/login'
})

export default router
