import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import DashboardView from './views/DashboardView.vue'
import DevicesView from './views/DevicesView.vue'
import WorkOrdersView from './views/WorkOrdersView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', component: DashboardView },
    { path: '/devices', component: DevicesView },
    { path: '/work-orders', component: WorkOrdersView },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.public && !localStorage.getItem('iot-ops-token')) return '/login'
})

export default router

