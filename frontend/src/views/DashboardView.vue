<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
interface Summary { devices: number; activeWorkOrders: number; openAlarms: number; badMessages: number; timeoutFailures: number }
const summary = ref<Summary>({ devices: 0, activeWorkOrders: 0, openAlarms: 0, badMessages: 0, timeoutFailures: 0 })
onMounted(async () => {
  try { summary.value = (await api.get<ApiResponse<Summary>>('/dashboard/summary')).data.data }
  catch (error: any) { ElMessage.error(readableError(error, '加载运维总览失败')) }
})
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">运维态势</span><h1>平台总览</h1></div></div>
    <div class="hero-panel">
      <div><span class="status-dot"></span> 系统运行正常</div>
      <h2>从设备交付记录到运维闭环</h2>
      <p>统一维护设备台账，将现场问题组织为多设备工单，并保留每一次业务状态变化。</p>
    </div>
    <div class="metric-grid">
      <el-card shadow="never"><span>可见设备</span><strong>{{ summary.devices }}</strong><small>已按项目数据权限过滤</small></el-card>
      <el-card shadow="never"><span>进行中工单</span><strong>{{ summary.activeWorkOrders }}</strong><small>包含待接单、处理中和待验收</small></el-card>
      <el-card shadow="never"><span>未关闭告警</span><strong>{{ summary.openAlarms }}</strong><small>{{ summary.badMessages + summary.timeoutFailures }} 项需要恢复处理</small></el-card>
    </div>
  </section>
</template>
