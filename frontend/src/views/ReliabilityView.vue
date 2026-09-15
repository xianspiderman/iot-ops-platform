<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type ApiResponse } from '../api'
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
  if (session.can('alarm:read')) alarms.value = (await api.get<ApiResponse<PageResult<Alarm>>>('/alarms')).data.data.records
  if (session.can('alarm:recover')) alarmFailures.value = (await api.get<ApiResponse<PageResult<AlarmFailure>>>('/alarm-events/failures')).data.data.records
  if (session.can('timeout:read')) {
    executions.value = (await api.get<ApiResponse<PageResult<Execution>>>('/timeout-inspections/executions')).data.data.records
    timeoutFailures.value = (await api.get<ApiResponse<PageResult<TimeoutFailure>>>('/timeout-inspections/failures')).data.data.records
  }
}

async function runInspection() {
  running.value = true
  try {
    const execution = (await api.post<ApiResponse<Execution>>('/timeout-inspections/run')).data.data
    ElMessage.success(`Inspection finished: ${execution.successCount} marked, ${execution.failureCount} failed`)
    await load()
  } catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Inspection failed') }
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
    ElMessage.success('Correction confirmed; the event is ready for replay')
    await load()
  } catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Invalid correction') }
}

async function replay(id: number) {
  try {
    const outcome = (await api.post<ApiResponse<string>>(`/alarm-events/failures/${id}/reprocess`)).data.data
    ElMessage.success(`Replay result: ${outcome}`)
    await load()
  } catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Replay failed') }
}

async function compensate(id: number) {
  try {
    await api.post(`/timeout-inspections/failures/${id}/compensate`)
    ElMessage.success('Compensation completed')
    await load()
  } catch (error: any) { ElMessage.error(error.response?.data?.message ?? 'Compensation failed') }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">FAILURE RECOVERY</span><h1>Reliability console</h1></div>
      <el-button v-if="session.can('timeout:execute')" type="primary" :loading="running" @click="runInspection">Run timeout inspection</el-button>
    </div>
    <el-tabs>
      <el-tab-pane v-if="session.can('alarm:read')" label="Alarms">
        <el-table :data="alarms" class="data-table">
          <el-table-column prop="eventId" label="Event ID" min-width="190" />
          <el-table-column prop="deviceId" label="Device" width="90" />
          <el-table-column prop="eventType" label="Type" width="190" />
          <el-table-column prop="severity" label="Severity" width="110" />
          <el-table-column prop="occurredAt" label="Occurred" width="190" />
          <el-table-column prop="status" label="Status" width="100" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-if="session.can('alarm:recover')" :label="`Bad messages (${alarmFailures.length})`">
        <el-table :data="alarmFailures" class="data-table">
          <el-table-column prop="eventId" label="Event ID" min-width="180" />
          <el-table-column prop="processStatus" label="State" width="190" />
          <el-table-column prop="failureReason" label="Reason" min-width="260" />
          <el-table-column prop="attemptCount" label="Attempts" width="100" />
          <el-table-column label="Recovery" width="190"><template #default="scope">
            <el-button link type="primary" @click="openCorrection(scope.row)">Correct</el-button>
            <el-button link @click="replay(scope.row.id)">Replay</el-button>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-if="session.can('timeout:read')" label="Inspection runs">
        <el-table :data="executions" class="data-table">
          <el-table-column prop="executionKey" label="Execution" min-width="260" />
          <el-table-column prop="triggerSource" label="Trigger" width="110" />
          <el-table-column prop="status" label="Status" width="150" />
          <el-table-column prop="scannedCount" label="Scanned" width="100" />
          <el-table-column prop="successCount" label="Marked" width="90" />
          <el-table-column prop="failureCount" label="Failed" width="90" />
          <el-table-column prop="startedAt" label="Started" width="190" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane v-if="session.can('timeout:read')" :label="`Inspection failures (${timeoutFailures.length})`">
        <el-table :data="timeoutFailures" class="data-table">
          <el-table-column prop="workOrderId" label="Work order" width="120" />
          <el-table-column prop="status" label="State" width="110" />
          <el-table-column prop="failureReason" label="Reason" min-width="300" />
          <el-table-column prop="attemptCount" label="Attempts" width="100" />
          <el-table-column label="Recovery" width="130"><template #default="scope">
            <el-button v-if="scope.row.status === 'PENDING' && session.can('timeout:execute')" link type="primary" @click="compensate(scope.row.id)">Compensate</el-button>
          </template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="correctionVisible" title="Confirm corrected alarm payload" width="720px">
      <el-input v-model="correctedPayload" type="textarea" :rows="12" />
      <template #footer><el-button @click="correctionVisible = false">Cancel</el-button><el-button type="primary" @click="confirmCorrection">Confirm</el-button></template>
    </el-dialog>
  </section>
</template>
