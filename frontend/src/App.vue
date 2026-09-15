<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from './stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const sessionReady = ref(false)

function logout() {
  session.clear()
  router.push('/login')
}

onMounted(async () => {
  try { await session.load() }
  catch { session.clear() }
  finally { sessionReady.value = true }
})
</script>

<template>
  <router-view v-if="route.meta.public" />
  <el-container v-else-if="sessionReady" class="shell">
    <el-aside width="228px" class="sidebar">
      <div class="brand"><span class="brand-mark">IO</span><span>IoT Ops</span></div>
      <el-menu router :default-active="route.path" background-color="transparent" text-color="#b9c6d6" active-text-color="#ffffff">
        <el-menu-item index="/"><span>运维总览</span></el-menu-item>
        <el-menu-item v-if="session.can('device:read')" index="/devices"><span>设备台账</span></el-menu-item>
        <el-menu-item v-if="session.can('device:import')" index="/device-imports"><span>设备导入</span></el-menu-item>
        <el-menu-item v-if="session.can('device:read')" index="/taxonomy"><span>分组与标签</span></el-menu-item>
        <el-menu-item v-if="session.can('work-order:read')" index="/work-orders"><span>工单管理</span></el-menu-item>
        <el-menu-item v-if="session.can('alarm:read') || session.can('timeout:read')" index="/reliability"><span>可靠性中心</span></el-menu-item>
        <el-menu-item v-if="session.can('rbac:manage')" index="/access"><span>权限管理</span></el-menu-item>
        <el-menu-item v-if="session.can('audit:read')" index="/audit"><span>业务审计</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <span class="environment">设备运维控制台</span>
        <el-button text @click="logout">退出登录</el-button>
      </el-header>
      <el-main class="content"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
