export function displayLabel(labels: Record<string, string>, value?: string | null) {
  if (!value) return '—'
  return labels[value] ?? value
}

export const onlineStatusLabels: Record<string, string> = {
  ONLINE: '在线', OFFLINE: '离线', UNKNOWN: '未知', FAULT: '故障',
}

export const workOrderStatusLabels: Record<string, string> = {
  WAITING: '待接单', PROCESSING: '处理中', WAIT_VERIFY: '待验收', CLOSED: '已关闭', CANCELED: '已取消',
}

export const priorityLabels: Record<string, string> = {
  HIGH: '高', NORMAL: '普通', LOW: '低',
}

export const importStatusLabels: Record<string, string> = {
  PROCESSING: '处理中', SUCCESS: '全部成功', PARTIAL_FAILED: '部分失败', FAILED: '系统失败',
}

export const alarmStatusLabels: Record<string, string> = {
  OPEN: '待处理', ACKNOWLEDGED: '已确认', CLOSED: '已关闭',
}

export const alarmProcessStatusLabels: Record<string, string> = {
  RECEIVED: '已接收', BUSINESS_BAD_MESSAGE: '业务坏消息', SYSTEM_FAILURE: '系统失败',
  CONFIRMED: '已确认修正', PROCESSED: '处理成功', DUPLICATE: '重复消息（已幂等处理）',
}

const importErrorLabels: Record<string, string> = {
  'SN is required': 'SN 为必填项',
  'SN format is invalid': 'SN 格式不正确',
  'Device name is required': '设备名称为必填项',
  'Device name exceeds 128 characters': '设备名称超过 128 个字符',
  'Project code is required': '项目编码为必填项',
  'Product code is required': '产品编码为必填项',
  'Project code does not exist': '项目编码不存在',
  'Product code does not exist': '产品编码不存在',
  'SN is duplicated in this file': 'SN 在当前文件内重复',
  'SN already exists in the database': 'SN 已存在于数据库',
}

export function localizeImportError(value?: string | null) {
  if (!value) return '—'
  return Object.entries(importErrorLabels).reduce(
    (localized, [source, target]) => localized.replaceAll(source, target),
    value,
  )
}

const failureReasonLabels: Record<string, string> = {
  'eventId is required': '缺少 eventId',
  'eventId must not exceed 96 characters': 'eventId 不能超过 96 个字符',
  'deviceSn is required': '缺少 deviceSn',
  'Unsupported eventType': '不支持的事件类型',
  'Unsupported severity': '不支持的告警级别',
  'occurredAt is required': '缺少 occurredAt',
  'Payload is not valid JSON': '消息体不是有效的 JSON',
  'Unknown inspection failure': '巡检发生未知异常',
  'demo timeout track failure': '演示用操作轨迹写入失败',
}

export function localizeFailureReason(value?: string | null) {
  if (!value) return '—'
  if (failureReasonLabels[value]) return failureReasonLabels[value]
  if (value.startsWith('Device SN does not exist:')) {
    return value.replace('Device SN does not exist:', '设备 SN 不存在：')
  }
  if (/\p{Script=Han}/u.test(value)) return value
  return '系统异常（原始诊断详情可通过 OpenAPI 或应用日志查看）'
}

const trackRemarkLabels: Record<string, string> = {
  'Work order created': '工单已创建',
  'Work order accepted': '工单已接单',
  'Work order submitted for verification': '工单已提交验收',
  'Verified and closed': '工单已验收关闭',
  'Deadline exceeded during scheduled inspection': '周期巡检发现工单已超过期限',
}

export function localizeTrackRemark(value?: string | null) {
  if (!value) return '—'
  return trackRemarkLabels[value] ?? value
}

export const severityLabels: Record<string, string> = {
  CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低',
}

export const eventTypeLabels: Record<string, string> = {
  DEVICE_OFFLINE: '设备离线', LOW_BATTERY: '电量过低', HIGH_TEMPERATURE: '温度过高',
}

export const executionStatusLabels: Record<string, string> = {
  RUNNING: '执行中', SUCCESS: '成功', PARTIAL_FAILED: '部分失败', FAILED: '失败',
}

export const failureStatusLabels: Record<string, string> = {
  PENDING: '待补偿', RESOLVED: '已解决',
}

export const triggerSourceLabels: Record<string, string> = {
  MANUAL: '管理端手动', XXL_JOB: 'XXL-JOB', TEST: '测试', TEST_RECOVERY: '测试恢复',
}

export const trackActionLabels: Record<string, string> = {
  CREATE: '创建工单', ACCEPT: '接单', SUBMIT: '提交验收', VERIFY_CLOSE: '验收关闭',
  CANCEL: '取消工单', TRANSFER: '转派', TIMEOUT_MARK: '标记超时',
}

export const operatorTypeLabels: Record<string, string> = {
  USER: '用户', SYSTEM: '系统',
}

export const enabledStatusLabels: Record<string, string> = {
  ENABLED: '启用', DISABLED: '停用',
}

export const auditActionLabels: Record<string, string> = {
  USER_CREATE: '创建用户', ROLE_CREATE: '创建角色', ROLE_PERMISSIONS_REPLACE: '更新角色权限',
  USER_ROLES_REPLACE: '更新用户角色', USER_DATA_SCOPE_REPLACE: '更新数据范围',
}

export const auditTargetLabels: Record<string, string> = {
  USER: '用户', ROLE: '角色', PERMISSION: '权限', DATA_SCOPE: '数据范围',
}

export const permissionLabels: Record<string, string> = {
  'project:read': '查看项目', 'project:write': '管理项目',
  'product:read': '查看产品', 'product:write': '管理产品',
  'device:read': '查看设备', 'device:write': '管理设备', 'device:import': '导入设备',
  'work-order:read': '查看工单', 'work-order:write': '处理工单',
  'alarm:read': '查看告警', 'alarm:recover': '恢复告警',
  'timeout:read': '查看巡检', 'timeout:execute': '执行巡检与补偿',
  'rbac:manage': '管理权限', 'audit:read': '查看审计',
  'device-taxonomy:write': '管理设备分组与标签',
}
