<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { api, type ApiResponse } from '../api'

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
  tasks.value = (await api.get<ApiResponse<PageResult<ImportTask>>>('/device-imports')).data.data.records
}

function choose(file: UploadFile) {
  selected.value = file.raw
}

async function upload() {
  if (!selected.value) return ElMessage.warning('Select an Excel workbook first')
  const body = new FormData()
  body.append('file', selected.value)
  uploading.value = true
  try {
    const task = (await api.post<ApiResponse<ImportTask>>('/device-imports', body)).data.data
    ElMessage.success(`Import finished: ${task.successRows} accepted, ${task.errorRows} rejected`)
    selected.value = undefined
    await load()
  } catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Import failed') }
  finally { uploading.value = false }
}

async function showErrors(id: number) {
  activeTaskId.value = id
  errorRows.value = (await api.get<ApiResponse<ImportError[]>>(`/device-imports/${id}/errors`)).data.data
  errorDialog.value = true
}

async function downloadErrors(id: number) {
  const response = await api.get(`/device-imports/${id}/errors.csv`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `import-errors-${id}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">BULK ONBOARDING</span><h1>Device imports</h1></div></div>
    <el-card shadow="never" class="import-panel">
      <el-upload :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="choose" :show-file-list="true">
        <el-button>Select workbook</el-button>
      </el-upload>
      <el-button type="primary" :loading="uploading" @click="upload">Validate and import</el-button>
      <small>Columns: SN, Device Name, Project Code, Product Code, IMEI, MAC, Firmware Version.</small>
    </el-card>
    <el-table :data="tasks" class="data-table">
      <el-table-column prop="id" label="Task" width="80" />
      <el-table-column prop="fileName" label="File" min-width="220" />
      <el-table-column prop="status" label="Status" width="150" />
      <el-table-column prop="totalRows" label="Rows" width="80" />
      <el-table-column prop="successRows" label="Accepted" width="100" />
      <el-table-column prop="errorRows" label="Rejected" width="100" />
      <el-table-column label="Result" width="180"><template #default="scope">
        <el-button v-if="scope.row.errorRows" link type="primary" @click="showErrors(scope.row.id)">View errors</el-button>
        <el-button v-if="scope.row.errorRows" link @click="downloadErrors(scope.row.id)">CSV</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="errorDialog" :title="`Rejected rows · task ${activeTaskId}`" width="760px">
      <el-table :data="errorRows" max-height="480">
        <el-table-column prop="rowNo" label="Row" width="80" />
        <el-table-column prop="sn" label="SN" width="180" />
        <el-table-column prop="errorMessage" label="Reason" min-width="360" />
      </el-table>
    </el-dialog>
  </section>
</template>
