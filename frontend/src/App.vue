<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from './stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

function logout() {
  session.clear()
  router.push('/login')
}

onMounted(() => session.load().catch(() => session.clear()))
</script>

<template>
  <router-view v-if="route.meta.public" />
  <el-container v-else class="shell">
    <el-aside width="228px" class="sidebar">
      <div class="brand"><span class="brand-mark">IO</span><span>IoT Ops</span></div>
      <el-menu router :default-active="route.path" background-color="transparent" text-color="#b9c6d6" active-text-color="#ffffff">
        <el-menu-item index="/"><span>Overview</span></el-menu-item>
        <el-menu-item v-if="session.can('device:read')" index="/devices"><span>Devices</span></el-menu-item>
        <el-menu-item v-if="session.can('device:import')" index="/device-imports"><span>Device imports</span></el-menu-item>
        <el-menu-item v-if="session.can('device:read')" index="/taxonomy"><span>Groups & tags</span></el-menu-item>
        <el-menu-item v-if="session.can('work-order:read')" index="/work-orders"><span>Work orders</span></el-menu-item>
        <el-menu-item v-if="session.can('alarm:read') || session.can('timeout:read')" index="/reliability"><span>Reliability</span></el-menu-item>
        <el-menu-item v-if="session.can('rbac:manage')" index="/access"><span>Access control</span></el-menu-item>
        <el-menu-item v-if="session.can('audit:read')" index="/audit"><span>Audit log</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <span class="environment">OPERATIONS CONSOLE</span>
        <el-button text @click="logout">Sign out</el-button>
      </el-header>
      <el-main class="content"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
