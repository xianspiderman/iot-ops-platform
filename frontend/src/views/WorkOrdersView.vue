<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type ApiResponse } from '../api'

interface WorkOrder { id: number; workOrderNo: string; title: string; priority: string; status: string; deadlineTime: string }
interface PageResult<T> { total: number; records: T[] }

const rows = ref<WorkOrder[]>([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ projectId: 1, deviceIdsText: '1,2', title: '', description: '', priority: 'NORMAL' })

async function load() {
  loading.value = true
  try { rows.value = (await api.get<ApiResponse<PageResult<WorkOrder>>>('/work-orders')).data.data.records }
  finally { loading.value = false }
}

async function create() {
  const deviceIds = form.deviceIdsText.split(',').map(Number).filter(Boolean)
  try {
    await api.post('/work-orders', { ...form, deviceIds })
    ElMessage.success('Work order created')
    dialog.value = false
    await load()
  } catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Create failed') }
}

async function accept(id: number) {
  try { await api.post(`/work-orders/${id}/accept`); ElMessage.success('Work order accepted'); await load() }
  catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Accept failed') }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">FIELD SERVICE</span><h1>Work orders</h1></div><el-button type="primary" @click="dialog = true">New work order</el-button></div>
    <el-table :data="rows" v-loading="loading" class="data-table">
      <el-table-column prop="workOrderNo" label="Number" min-width="190" />
      <el-table-column prop="title" label="Title" min-width="240" />
      <el-table-column prop="priority" label="Priority" width="110" />
      <el-table-column prop="status" label="Status" width="130" />
      <el-table-column label="Action" width="120"><template #default="scope"><el-button v-if="scope.row.status === 'WAITING'" link type="primary" @click="accept(scope.row.id)">Accept</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="Create work order" width="560px">
      <el-form label-position="top">
        <el-form-item label="Title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="Description"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <div class="form-grid"><el-form-item label="Project ID"><el-input-number v-model="form.projectId" :min="1" /></el-form-item><el-form-item label="Device IDs"><el-input v-model="form.deviceIdsText" placeholder="1,2" /></el-form-item></div>
        <el-form-item label="Priority"><el-select v-model="form.priority"><el-option label="High" value="HIGH" /><el-option label="Normal" value="NORMAL" /><el-option label="Low" value="LOW" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">Cancel</el-button><el-button type="primary" @click="create">Create</el-button></template>
    </el-dialog>
  </section>
</template>

