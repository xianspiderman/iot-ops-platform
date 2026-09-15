<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
import { useSessionStore } from '../stores/session'

const router = useRouter()
const loading = ref(false)
const session = useSessionStore()
const form = reactive({ username: 'admin', password: 'Admin@123' })

async function submit() {
  loading.value = true
  try {
    const response = await api.post<ApiResponse<{ tokenValue: string }>>('/auth/login', form)
    localStorage.setItem('iot-ops-token', response.data.data.tokenValue)
    await session.load()
    await router.push('/')
  } catch (error: any) {
    ElMessage.error(readableError(error, '登录失败，请稍后重试'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-copy">
      <span class="eyebrow">设备交付 · 现场运维</span>
      <h1>让每一次设备问题都有记录、有处理、有闭环。</h1>
      <p>统一管理设备台账、告警、工单、巡检和失败恢复。</p>
    </section>
    <el-card class="login-card" shadow="never">
      <h2>登录运维平台</h2>
      <p>使用演示账号进入管理端。</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="wide">登录</el-button>
      </el-form>
    </el-card>
  </main>
</template>
