<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, readableError, type ApiResponse } from '../api'
import { alarmProcessStatusLabels, alarmStatusLabels, displayLabel, eventTypeLabels, executionStatusLabels, failureStatusLabels, localizeFailureReason, severityLabels, triggerSourceLabels } from '../labels'
import { useSessionStore } from '../stores/session'

interface PageResult<T> { total: number; records: T[] }
interface Alarm { id: number; eventId: string; deviceId: number; eventType: string; severity: string; occurredAt: string; status: string }
interface AlarmFailure { id: number; eventId: string; processStatus: string; failureCategory?: string; failureReason?: string; attemptCount: number; correctedPayload?: string }
interface Execution { id: number; executionKey: string; triggerSource: string; status: string; scannedCount: number; successCount: number; skippedCount: number; failureCount: number; startedAt: string }
interface TimeoutFailure { id: number; workOrderId: number; status: string; failureReason: string; attemptCount: number; lastFailedAt: string }

const alarms = ref<Alarm[]>([])
const alarmFailures = ref<AlarmFailure[]>([])
const executions = ref<Execution[]>([])
const timeoutFailures = ref<TimeoutFailure[]>([])
const correctionVisible = ref(false)
const correcting = ref<AlarmFailure>()
const correctedPayload = ref('')
const running = ref(false)
const session = useSessionStore()

async function load() {
  try {
    if (session.can('alarm:read')) alarms.value = (await api.get<ApiResponse<PageResult<Alarm>>>('/alarms')).data.data.records
    if (session.can('alarm:recover')) alarmFailures.value = (await api.get<ApiResponse<PageResult<AlarmFailure>>>('/alarm-events/failures')).data.data.records
    if (session.can('timeout:read')) {
      executions.value = (await api.get<ApiResponse<PageResult<Execution>>>('/timeout-inspections/executions')).data.data.records
      timeoutFailures.value = (await api.get<ApiResponse<PageResult<TimeoutFailure>>>('/timeout-inspections/failures')).data.data.records
    }
  } catch (error: any) { ElMessage.error(readableError(error, '加载可靠性数据失败')) }
}

async function runInspection() {
  running.value = true
  try {
    const execution = (await api.post<ApiResponse<Execution>>('/timeout-inspections/run')).data.data
    ElMessage.success(`巡检完成：标记 ${execution.successCount} 条，失败 ${execution.failureCount} 条`)
    await load()
  } catch (error: any) { ElMessage.error(readableError(error, '执行超时巡检失败')) }
  finally { running.value = false }
}

function openCorrection(row: AlarmFailure) {
  correcting.value = row
  correctedPayload.value = row.correctedPayload ?? `{"eventId":"${row.eventId}","deviceSn":"DEMO-SN-001","eventType":"LOW_BATTERY","severity":"HIGH","occurredAt":"2026-09-15T10:00:00","data":{}}`
  correctionVisible.value = true
}

async function confirmCorrection() {
  if (!correcting.value) return
  try {
    await api.post(`/alarm-events/failures/${correcting.value.id}/confirm`, { correctedPayload: JSON.parse(correctedPayload.value) })
    correctionVisible.value = false
    ElMessage.success('修正内容已确认，可以重新处理该事件')
    await load()
  } catch (error: any) { ElMessage.error(readableError(error, '告警修正内容无效')) }
}

async function replay(id: number) {
  try {
    const outcome = (await api.post<ApiResponse<string>>(`/alarm-events/failures/${id}/reprocess`)).data.data
    ElMessage.success(`重新处理结果：${displayLabel(alarmProcessStatusLabels, outcome)}`)
    await load()
  } catch (error: any) { ElMessage.error(readableError(error, '重新处理告警失败')) }
}

async function compensate(id: number) {
  try {
    await api.post(`/timeout-inspections/failures/${id}/compensate`)
    ElMessage.success('人工补偿完成')
    await load()
  } catch (error: any) { ElMessage.error(readableError(error, '人工补偿失败')) }
}

function formatTime(value?: string) { return value ? value.replace('T', ' ') : '—' }

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">失败恢复</span><h1>可靠性中心</h1></div>
      <el-button v-if="session.can('timeout:execute')" type="primary" :loading="running" @click="runInspection">执行超时巡检</el-button>
    </div>
    <el-tabs>
      <el-tab-pane v-if="session.can('alarm:read')" label="告警记录">
        <el-table :data="alarms" class="data-table">
          <el-table-column prop="eventId" label="eventId" min-width="190" />
          <el-table-column prop="deviceId" label="设备 ID" width="90" />
          <el-table-column prop="eventType" label="事件类型" width="150"><template #default="scope">{{ displayLabel(eventTypeLabels, scope.row.eventType) }}</template></el-table-column>
          <el-table-column prop="severity" label="级别" width="90"><template #default="scope">{{ displayLabel(severityLabels, scope.row.severity) }}</template></el-table-column>
          <el-table-column prop="occurredAt" label="发生时间" width="190"><template #default="scope">{{ formatTime(scope.row.occurredAt) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="100"><template #default="scope">{{ displayLabel(alarmStatusLabels, scope.row.status) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-if="session.can('alarm:recover')" :label="`坏消息与恢复（${alarmFailures.length}）`">
        <el-table :data="alarmFailures" class="data-table">
          <el-table-column prop="eventId" label="eventId" min-width="180" />
          <el-table-column prop="processStatus" label="处理状态" width="140"><template #default="scope">{{ displayLabel(alarmProcessStatusLabels, scope.row.processStatus) }}</template></el-table-column>
          <el-table-column prop="failureReason" label="失败原因" min-width="260"><template #default="scope">{{ localizeFailureReason(scope.row.failureReason) }}</template></el-table-column>
          <el-table-column prop="attemptCount" label="尝试次数" width="100" />
          <el-table-column label="恢复操作" width="190"><template #default="scope">
            <el-button link type="primary" @click="openCorrection(scope.row)">确认修正</el-button>
            <el-button link @click="replay(scope.row.id)">重新处理</el-button>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-if="session.can('timeout:read')" label="巡检执行记录">
        <el-table :data="executions" class="data-table">
          <el-table-column prop="executionKey" label="执行标识" min-width="260" />
          <el-table-column prop="triggerSource" label="触发来源" width="120"><template #default="scope">{{ displayLabel(triggerSourceLabels, scope.row.triggerSource) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="110"><template #default="scope">{{ displayLabel(executionStatusLabels, scope.row.status) }}</template></el-table-column>
          <el-table-column prop="scannedCount" label="扫描数" width="90" />
          <el-table-column prop="successCount" label="成功标记" width="90" />
          <el-table-column prop="skippedCount" label="跳过" width="80" />
          <el-table-column prop="failureCount" label="失败" width="80" />
          <el-table-column prop="startedAt" label="开始时间" width="190"><template #default="scope">{{ formatTime(scope.row.startedAt) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-if="session.can('timeout:read')" :label="`巡检失败记录（${timeoutFailures.length}）`">
        <el-table :data="timeoutFailures" class="data-table">
          <el-table-column prop="workOrderId" label="工单 ID" width="100" />
          <el-table-column prop="status" label="状态" width="110"><template #default="scope">{{ displayLabel(failureStatusLabels, scope.row.status) }}</template></el-table-column>
          <el-table-column prop="failureReason" label="失败原因" min-width="300"><template #default="scope">{{ localizeFailureReason(scope.row.failureReason) }}</template></el-table-column>
          <el-table-column prop="attemptCount" label="尝试次数" width="100" />
          <el-table-column label="恢复操作" width="130"><template #default="scope">
            <el-button v-if="scope.row.status === 'PENDING' && session.can('timeout:execute')" link type="primary" @click="compensate(scope.row.id)">人工补偿</el-button>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="correctionVisible" title="确认修正后的告警消息" width="720px">
      <el-input v-model="correctedPayload" type="textarea" :rows="12" />
      <template #footer><el-button @click="correctionVisible = false">取消</el-button><el-button type="primary" @click="confirmCorrection">确认修正</el-button></template>
    </el-dialog>
  </section>
</template>
