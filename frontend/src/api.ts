import axios from 'axios'

export interface ApiResponse<T> {
  success: boolean
  data: T
  code?: string
  message?: string
}

const errorLabels: Record<string, string> = {
  LOGIN_FAILED: '用户名或密码错误', UNAUTHORIZED: '登录状态已失效，请重新登录', FORBIDDEN: '当前账号没有此操作权限',
  VALIDATION_ERROR: '提交内容不完整或格式不正确', DATA_CONFLICT: '数据与现有记录冲突', INTERNAL_ERROR: '系统暂时无法完成操作',
  DEVICE_SN_EXISTS: '设备 SN 已存在', DEVICE_NOT_FOUND: '设备不存在', DEVICE_REQUIRED: '至少选择一台设备',
  DEVICE_PROJECT_MISMATCH: '设备与工单不属于同一项目', DEVICE_GROUP_NOT_FOUND: '设备分组不存在',
  DEVICE_GROUP_PROJECT_MISMATCH: '设备与分组不属于同一项目', DEVICE_TAG_NOT_FOUND: '设备标签不存在',
  PROJECT_NOT_FOUND: '项目不存在', PROJECT_CODE_EXISTS: '项目编码已存在',
  PRODUCT_NOT_FOUND: '产品不存在', PRODUCT_CODE_EXISTS: '产品编码已存在',
  IMPORT_FILE_EMPTY: '请选择非空的 Excel 文件', IMPORT_FILE_INVALID: 'Excel 文件无法解析或格式不正确',
  IMPORT_ROW_LIMIT: '导入行数超过系统限制', IMPORT_TASK_NOT_FOUND: '导入任务不存在',
  WORK_ORDER_NOT_FOUND: '工单不存在', WORK_ORDER_NOT_WAITING: '工单已被接单或不处于待接单状态',
  WORK_ORDER_SUBMIT_CONFLICT: '只有当前处理人可以提交该工单', WORK_ORDER_VERIFY_CONFLICT: '工单不处于待验收状态',
  WORK_ORDER_CANCEL_CONFLICT: '工单状态已经变化，请刷新后重试', WORK_ORDER_TRANSFER_CONFLICT: '工单转派冲突，请刷新后重试',
  INVALID_WORK_ORDER_STATE: '工单状态无效', INVALID_WORK_ORDER_TRANSITION: '不允许执行该状态流转',
  INVALID_PRIORITY: '工单优先级无效', HANDLER_NOT_AVAILABLE: '目标处理人不存在或已停用',
  ALARM_EVENT_NOT_FOUND: '告警事件记录不存在', ALARM_EVENT_ALREADY_PROCESSED: '告警事件已经处理成功',
  ALARM_EVENT_STATE_CHANGED: '告警事件状态已经变化，请刷新后重试', ALARM_EVENT_STILL_INVALID: '修正后的告警仍不符合业务规则',
  EVENT_ID_IMMUTABLE: '修正时不能改变 eventId', INVALID_CORRECTION: '修正后的消息格式不正确',
  TIMEOUT_FAILURE_NOT_FOUND: '巡检失败记录不存在', WORK_ORDER_NOT_TIMEOUT_CANDIDATE: '该工单当前不满足超时标记条件',
  USER_NOT_FOUND: '用户不存在', USERNAME_EXISTS: '用户名已存在', ROLE_NOT_FOUND: '角色不存在',
  ROLE_CODE_EXISTS: '角色编码已存在', PERMISSION_NOT_FOUND: '权限不存在', DATA_SCOPE_DENIED: '当前数据范围不允许执行此操作',
}

export function readableError(error: any, fallback: string) {
  return errorLabels[error?.response?.data?.code] ?? fallback
}

export const api = axios.create({ baseURL: '/api', timeout: 15_000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('iot-ops-token')
  if (token) config.headers['X-Token'] = token
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('iot-ops-token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)
