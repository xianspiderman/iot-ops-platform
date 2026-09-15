<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type ApiResponse } from '../api'

interface Device { id: number; sn: string; deviceName: string; projectId: number; productId: number; onlineStatus: string }
interface PageResult<T> { total: number; records: T[] }

const rows = ref<Device[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ sn: '', deviceName: '', projectId: 1, productId: 1, imei: '', mac: '', firmwareVersion: '' })

async function load() {
  loading.value = true
  try {
    const response = await api.get<ApiResponse<PageResult<Device>>>('/devices')
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } finally { loading.value = false }
}

async function create() {
  try {
    await api.post('/devices', form)
    ElMessage.success('Device created')
    dialog.value = false
    await load()
  } catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Create failed') }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">INVENTORY</span><h1>Devices <small>{{ total }}</small></h1></div><el-button type="primary" @click="dialog = true">Add device</el-button></div>
    <el-table :data="rows" v-loading="loading" class="data-table">
      <el-table-column prop="sn" label="SN" min-width="160" />
      <el-table-column prop="deviceName" label="Name" min-width="220" />
      <el-table-column prop="projectId" label="Project" width="100" />
      <el-table-column prop="productId" label="Product" width="100" />
      <el-table-column prop="onlineStatus" label="Status" width="120"><template #default="scope"><el-tag effect="plain">{{ scope.row.onlineStatus }}</el-tag></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="Register device" width="520px">
      <el-form label-position="top">
        <div class="form-grid"><el-form-item label="SN"><el-input v-model="form.sn" /></el-form-item><el-form-item label="Name"><el-input v-model="form.deviceName" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="Project ID"><el-input-number v-model="form.projectId" :min="1" /></el-form-item><el-form-item label="Product ID"><el-input-number v-model="form.productId" :min="1" /></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="dialog = false">Cancel</el-button><el-button type="primary" @click="create">Create</el-button></template>
    </el-dialog>
  </section>
</template>

