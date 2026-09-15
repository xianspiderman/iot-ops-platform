<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
import { displayLabel, importStatusLabels, localizeImportError } from '../labels'

interface ImportTask { id: number; fileName: string; status: string; totalRows: number; successRows: number; errorRows: number; failureReason?: string; createdAt: string }
interface ImportError { id: number; rowNo: number; sn?: string; errorMessage: string }
interface PageResult<T> { total: number; records: T[] }

const tasks = ref<ImportTask[]>([])
const selected = ref<File>()
const uploading = ref(false)
const errorRows = ref<ImportError[]>([])
const errorDialog = ref(false)
const activeTaskId = ref<number>()

async function load() {
  try { tasks.value = (await api.get<ApiResponse<PageResult<ImportTask>>>('/device-imports')).data.data.records }
  catch (error: any) { ElMessage.error(readableError(error, '加载导入任务失败')) }
}

function choose(file: UploadFile) {
  selected.value = file.raw
}

async function upload() {
  if (!selected.value) return ElMessage.warning('请先选择 Excel 工作簿')
  const body = new FormData()
  body.append('file', selected.value)
  uploading.value = true
  try {
    const task = (await api.post<ApiResponse<ImportTask>>('/device-imports', body)).data.data
    ElMessage.success(`导入完成：成功 ${task.successRows} 行，失败 ${task.errorRows} 行`)
    selected.value = undefined
    await load()
  } catch (error: any) { ElMessage.error(readableError(error, '设备导入失败')) }
  finally { uploading.value = false }
}

async function showErrors(id: number) {
  try {
    activeTaskId.value = id
    errorRows.value = (await api.get<ApiResponse<ImportError[]>>(`/device-imports/${id}/errors`)).data.data
    errorDialog.value = true
  } catch (error: any) { ElMessage.error(readableError(error, '加载错误明细失败')) }
}

async function downloadErrors(id: number) {
  try {
    const response = await api.get(`/device-imports/${id}/errors.csv`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `import-errors-${id}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error: any) { ElMessage.error(readableError(error, '下载错误结果失败')) }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">批量交付</span><h1>设备导入</h1></div></div>
    <el-card shadow="never" class="import-panel">
      <el-upload :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="choose" :show-file-list="true">
        <el-button>选择工作簿</el-button>
      </el-upload>
      <el-button type="primary" :loading="uploading" @click="upload">校验并导入</el-button>
      <small>列顺序：SN、设备名称、项目编码、产品编码、IMEI、MAC、固件版本。</small>
    </el-card>
    <el-table :data="tasks" class="data-table">
      <el-table-column prop="id" label="任务 ID" width="90" />
      <el-table-column prop="fileName" label="文件名" min-width="220" />
      <el-table-column prop="status" label="状态" width="150"><template #default="scope">{{ displayLabel(importStatusLabels, scope.row.status) }}</template></el-table-column>
      <el-table-column prop="totalRows" label="总行数" width="90" />
      <el-table-column prop="successRows" label="成功" width="90" />
      <el-table-column prop="errorRows" label="失败" width="90" />
      <el-table-column label="结果" width="180"><template #default="scope">
        <el-button v-if="scope.row.errorRows" link type="primary" @click="showErrors(scope.row.id)">查看错误</el-button>
        <el-button v-if="scope.row.errorRows" link @click="downloadErrors(scope.row.id)">CSV</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="errorDialog" :title="`错误行 · 任务 ${activeTaskId}`" width="760px">
      <el-table :data="errorRows" max-height="480">
        <el-table-column prop="rowNo" label="原始行号" width="100" />
        <el-table-column prop="sn" label="SN" width="180" />
        <el-table-column prop="errorMessage" label="错误原因" min-width="360">
          <template #default="scope">{{ localizeImportError(scope.row.errorMessage) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </section>
</template>
