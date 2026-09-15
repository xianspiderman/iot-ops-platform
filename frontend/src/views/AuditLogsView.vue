<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
import { auditActionLabels, auditTargetLabels, displayLabel } from '../labels'
interface AuditLog { id: number; operatorId?: number; action: string; targetType: string; targetId?: string; detail: string; requestId?: string; createdAt: string }
interface PageResult<T> { total: number; records: T[] }
const rows = ref<AuditLog[]>([])
const total = ref(0)
async function load() {
  try { const result = (await api.get<ApiResponse<PageResult<AuditLog>>>('/audit-logs')).data.data; rows.value = result.records; total.value = result.total }
  catch (error: any) { ElMessage.error(readableError(error, '加载业务审计失败')) }
}
onMounted(load)
function formatTime(value?: string) { return value ? value.replace('T', ' ') : '—' }
</script>
<template><section><div class="page-heading"><div><span class="eyebrow">责任追踪</span><h1>业务审计 <small>{{ total }}</small></h1></div></div><el-table :data="rows" class="data-table"><el-table-column prop="createdAt" label="时间" width="190"><template #default="scope">{{ formatTime(scope.row.createdAt) }}</template></el-table-column><el-table-column prop="operatorId" label="操作人 ID" width="100" /><el-table-column prop="action" label="操作动作" min-width="190"><template #default="scope">{{ displayLabel(auditActionLabels, scope.row.action) }}</template></el-table-column><el-table-column prop="targetType" label="目标类型" width="150"><template #default="scope">{{ displayLabel(auditTargetLabels, scope.row.targetType) }}</template></el-table-column><el-table-column prop="targetId" label="目标 ID" width="100" /><el-table-column prop="requestId" label="requestId" min-width="220" /><el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip /></el-table></section></template>
