<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
import { displayLabel, localizeTrackRemark, operatorTypeLabels, priorityLabels, trackActionLabels, workOrderStatusLabels } from '../labels'

interface WorkOrder { id: number; workOrderNo: string; title: string; description: string; priority: string; status: string; deadlineTime: string; timeoutFlag: boolean; timeoutTime?: string; deviceIds?: number[] }
interface Track { id: number; operatorId?: number; operatorType: string; action: string; beforeStatus?: string; afterStatus?: string; remark?: string; createdAt: string }
interface WorkOrderDetail { workOrder: WorkOrder; deviceIds: number[]; tracks: Track[] }
interface PageResult<T> { total: number; records: T[] }

const rows = ref<WorkOrder[]>([])
const loading = ref(false)
const dialog = ref(false)
const detailVisible = ref(false)
const detail = ref<WorkOrderDetail>()
const form = reactive({ projectId: 1, deviceIdsText: '1,2', title: '', description: '', priority: 'NORMAL' })

async function load() {
  loading.value = true
  try { rows.value = (await api.get<ApiResponse<PageResult<WorkOrder>>>('/work-orders')).data.data.records }
  catch (error: any) { ElMessage.error(readableError(error, '加载工单失败')) }
  finally { loading.value = false }
}

async function create() {
  const deviceIds = form.deviceIdsText.split(',').map(Number).filter(Boolean)
  try {
    await api.post('/work-orders', { ...form, deviceIds })
    ElMessage.success('工单创建成功')
    dialog.value = false
    await load()
  } catch (error: any) { ElMessage.error(readableError(error, '创建工单失败')) }
}

async function accept(id: number) {
  try { await api.post(`/work-orders/${id}/accept`); ElMessage.success('接单成功'); await load() }
  catch (error: any) { ElMessage.error(readableError(error, '接单失败')) }
}

async function submit(id: number) {
  try {
    const { value } = await ElMessageBox.prompt('请填写处理结果。', '提交验收', { inputType: 'textarea', inputValidator: (text) => !!text?.trim() || '处理结果不能为空' })
    await api.post(`/work-orders/${id}/submit`, { solution: value })
    ElMessage.success('已提交验收')
    await load()
  } catch (error: any) { if (error !== 'cancel') ElMessage.error(readableError(error, '提交验收失败')) }
}

async function verify(id: number) {
  try {
    const { value } = await ElMessageBox.prompt('可以填写验收说明。', '验收并关闭', { inputValue: '现场验证通过' })
    await api.post(`/work-orders/${id}/verify`, { remark: value })
    ElMessage.success('工单已验收关闭')
    await load()
  } catch (error: any) { if (error !== 'cancel') ElMessage.error(readableError(error, '工单验收失败')) }
}

async function cancel(id: number) {
  try {
    const { value } = await ElMessageBox.prompt('请填写取消原因。', '取消工单', { inputValidator: (text) => !!text?.trim() || '取消原因不能为空' })
    await api.post(`/work-orders/${id}/cancel`, { reason: value })
    ElMessage.success('工单已取消')
    await load()
  } catch (error: any) { if (error !== 'cancel') ElMessage.error(readableError(error, '取消工单失败')) }
}

async function showDetail(id: number) {
  try {
    detail.value = (await api.get<ApiResponse<WorkOrderDetail>>(`/work-orders/${id}`)).data.data
    detailVisible.value = true
  } catch (error: any) { ElMessage.error(readableError(error, '加载工单详情失败')) }
}

function formatTime(value?: string) { return value ? value.replace('T', ' ') : '—' }

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">现场服务</span><h1>工单管理</h1></div><el-button type="primary" @click="dialog = true">新建工单</el-button></div>
    <el-table :data="rows" v-loading="loading" class="data-table">
      <el-table-column prop="workOrderNo" label="工单编号" min-width="190" />
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="priority" label="优先级" width="90"><template #default="scope">{{ displayLabel(priorityLabels, scope.row.priority) }}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="110"><template #default="scope">{{ displayLabel(workOrderStatusLabels, scope.row.status) }}</template></el-table-column>
      <el-table-column label="超时" width="90"><template #default="scope"><el-tag :type="scope.row.timeoutFlag ? 'danger' : 'info'" effect="plain">{{ scope.row.timeoutFlag ? '已超时' : '未超时' }}</el-tag></template></el-table-column>
      <el-table-column label="设备数" width="90"><template #default="scope">{{ scope.row.deviceIds?.length ?? 0 }}</template></el-table-column>
      <el-table-column label="操作" min-width="300"><template #default="scope">
        <el-button link @click="showDetail(scope.row.id)">详情与轨迹</el-button>
        <el-button v-if="scope.row.status === 'WAITING'" link type="primary" @click="accept(scope.row.id)">接单</el-button>
        <el-button v-if="scope.row.status === 'PROCESSING'" link type="primary" @click="submit(scope.row.id)">提交验收</el-button>
        <el-button v-if="scope.row.status === 'WAIT_VERIFY'" link type="success" @click="verify(scope.row.id)">验收关闭</el-button>
        <el-button v-if="!['CLOSED', 'CANCELED'].includes(scope.row.status)" link type="danger" @click="cancel(scope.row.id)">取消</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="新建工单" width="560px">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="问题描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <div class="form-grid"><el-form-item label="项目 ID"><el-input-number v-model="form.projectId" :min="1" /></el-form-item><el-form-item label="设备 ID"><el-input v-model="form.deviceIdsText" placeholder="例如：1,2" /></el-form-item></div>
        <el-form-item label="优先级"><el-select v-model="form.priority"><el-option label="高" value="HIGH" /><el-option label="普通" value="NORMAL" /><el-option label="低" value="LOW" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="create">创建</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="工单详情与操作轨迹" size="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="工单编号">{{ detail.workOrder.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ displayLabel(workOrderStatusLabels, detail.workOrder.status) }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.workOrder.title }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ displayLabel(priorityLabels, detail.workOrder.priority) }}</el-descriptions-item>
          <el-descriptions-item label="到期时间">{{ formatTime(detail.workOrder.deadlineTime) }}</el-descriptions-item>
          <el-descriptions-item label="超时状态">{{ detail.workOrder.timeoutFlag ? `已超时（${formatTime(detail.workOrder.timeoutTime)}）` : '未超时' }}</el-descriptions-item>
          <el-descriptions-item label="关联设备" :span="2">{{ detail.deviceIds.join(', ') }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">{{ detail.workOrder.description }}</el-descriptions-item>
        </el-descriptions>
        <h3>操作轨迹</h3>
        <el-table :data="detail.tracks" class="data-table">
          <el-table-column prop="createdAt" label="时间" min-width="170"><template #default="scope">{{ formatTime(scope.row.createdAt) }}</template></el-table-column>
          <el-table-column prop="operatorType" label="操作方" width="90"><template #default="scope">{{ displayLabel(operatorTypeLabels, scope.row.operatorType) }}</template></el-table-column>
          <el-table-column prop="action" label="动作" width="120"><template #default="scope">{{ displayLabel(trackActionLabels, scope.row.action) }}</template></el-table-column>
          <el-table-column label="状态变化" min-width="170"><template #default="scope">{{ displayLabel(workOrderStatusLabels, scope.row.beforeStatus) }} → {{ displayLabel(workOrderStatusLabels, scope.row.afterStatus) }}</template></el-table-column>
          <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip><template #default="scope">{{ localizeTrackRemark(scope.row.remark) }}</template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </section>
</template>
