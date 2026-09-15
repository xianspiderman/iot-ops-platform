<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type ApiResponse } from '../api'
interface AuditLog { id: number; operatorId?: number; action: string; targetType: string; targetId?: string; detail: string; requestId?: string; createdAt: string }
interface PageResult<T> { total: number; records: T[] }
const rows = ref<AuditLog[]>([])
const total = ref(0)
async function load() { const result = (await api.get<ApiResponse<PageResult<AuditLog>>>('/audit-logs')).data.data; rows.value = result.records; total.value = result.total }
onMounted(load)
</script>
<template><section><div class="page-heading"><div><span class="eyebrow">ACCOUNTABILITY</span><h1>Business audit <small>{{ total }}</small></h1></div></div><el-table :data="rows" class="data-table"><el-table-column prop="createdAt" label="Time" width="190" /><el-table-column prop="operatorId" label="Operator" width="100" /><el-table-column prop="action" label="Action" min-width="190" /><el-table-column prop="targetType" label="Target" width="150" /><el-table-column prop="targetId" label="ID" width="100" /><el-table-column prop="requestId" label="Request ID" min-width="220" /><el-table-column prop="detail" label="Detail" min-width="280" show-overflow-tooltip /></el-table></section></template>
