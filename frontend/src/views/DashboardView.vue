<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type ApiResponse } from '../api'
interface Summary { devices: number; activeWorkOrders: number; openAlarms: number; badMessages: number; timeoutFailures: number }
const summary = ref<Summary>({ devices: 0, activeWorkOrders: 0, openAlarms: 0, badMessages: 0, timeoutFailures: 0 })
onMounted(async () => { summary.value = (await api.get<ApiResponse<Summary>>('/dashboard/summary')).data.data })
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">OPERATIONS</span><h1>Platform overview</h1></div></div>
    <div class="hero-panel">
      <div><span class="status-dot"></span> System ready</div>
      <h2>From delivery records to closed-loop maintenance</h2>
      <p>Create device records, group field issues into multi-device work orders, and keep every operational transition visible.</p>
    </div>
    <div class="metric-grid">
      <el-card shadow="never"><span>Visible devices</span><strong>{{ summary.devices }}</strong><small>Filtered by project data scope</small></el-card>
      <el-card shadow="never"><span>Active work orders</span><strong>{{ summary.activeWorkOrders }}</strong><small>Waiting, processing or verification</small></el-card>
      <el-card shadow="never"><span>Open alarms</span><strong>{{ summary.openAlarms }}</strong><small>{{ summary.badMessages + summary.timeoutFailures }} items require recovery</small></el-card>
    </div>
  </section>
</template>
