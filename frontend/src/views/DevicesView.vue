<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
import { displayLabel, onlineStatusLabels } from '../labels'

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
  } catch (error: any) { ElMessage.error(readableError(error, '加载设备台账失败')) }
  finally { loading.value = false }
}

async function create() {
  try {
    await api.post('/devices', form)
    ElMessage.success('设备创建成功')
    dialog.value = false
    await load()
  } catch (error: any) { ElMessage.error(readableError(error, '创建设备失败')) }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">设备资产</span><h1>设备台账 <small>{{ total }}</small></h1></div><el-button type="primary" @click="dialog = true">新增设备</el-button></div>
    <el-table :data="rows" v-loading="loading" class="data-table">
      <el-table-column prop="sn" label="SN" min-width="160" />
      <el-table-column prop="deviceName" label="设备名称" min-width="220" />
      <el-table-column prop="projectId" label="项目 ID" width="100" />
      <el-table-column prop="productId" label="产品 ID" width="100" />
      <el-table-column prop="onlineStatus" label="在线状态" width="120"><template #default="scope"><el-tag effect="plain">{{ displayLabel(onlineStatusLabels, scope.row.onlineStatus) }}</el-tag></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="登记设备" width="560px">
      <el-form label-position="top">
        <div class="form-grid"><el-form-item label="SN"><el-input v-model="form.sn" /></el-form-item><el-form-item label="设备名称"><el-input v-model="form.deviceName" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="项目 ID"><el-input-number v-model="form.projectId" :min="1" /></el-form-item><el-form-item label="产品 ID"><el-input-number v-model="form.productId" :min="1" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="IMEI"><el-input v-model="form.imei" /></el-form-item><el-form-item label="MAC"><el-input v-model="form.mac" /></el-form-item></div>
        <el-form-item label="固件版本"><el-input v-model="form.firmwareVersion" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="create">创建</el-button></template>
    </el-dialog>
  </section>
</template>
