<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, type ApiResponse } from '../api'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'Admin@123' })

async function submit() {
  loading.value = true
  try {
    const response = await api.post<ApiResponse<{ tokenValue: string }>>('/auth/login', form)
    localStorage.setItem('iot-ops-token', response.data.data.tokenValue)
    await router.push('/')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message ?? 'Sign in failed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-copy">
      <span class="eyebrow">DEVICE DELIVERY · FIELD OPERATIONS</span>
      <h1>Keep every device issue accountable.</h1>
      <p>One operational view for inventory, alarms, work orders and recovery.</p>
    </section>
    <el-card class="login-card" shadow="never">
      <h2>Welcome back</h2>
      <p>Use the demonstration account to enter the console.</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="Username"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="Password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="wide">Sign in</el-button>
      </el-form>
    </el-card>
  </main>
</template>

