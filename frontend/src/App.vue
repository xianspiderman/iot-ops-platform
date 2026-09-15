<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

function logout() {
  localStorage.removeItem('iot-ops-token')
  router.push('/login')
}
</script>

<template>
  <router-view v-if="route.meta.public" />
  <el-container v-else class="shell">
    <el-aside width="228px" class="sidebar">
      <div class="brand"><span class="brand-mark">IO</span><span>IoT Ops</span></div>
      <el-menu router :default-active="route.path" background-color="transparent" text-color="#b9c6d6" active-text-color="#ffffff">
        <el-menu-item index="/"><span>Overview</span></el-menu-item>
        <el-menu-item index="/devices"><span>Devices</span></el-menu-item>
        <el-menu-item index="/work-orders"><span>Work orders</span></el-menu-item>
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

