# API 使用约定

完整、与运行代码同步的接口定义由 SpringDoc 生成：

- Compose OpenAPI UI：`http://localhost:18080/api/swagger-ui/index.html`
- Compose OpenAPI JSON：`http://localhost:18080/api/v3/api-docs`
- 本地后端默认端口：`http://localhost:8080/api/swagger-ui/index.html`

## 认证与响应

调用 `POST /api/auth/login`：

```json
{"username":"admin","password":"Admin@123"}
```

响应 `data.tokenName` 和 `data.tokenValue` 分别是后续请求的 Header 名称和值。成功响应统一为：

```json
{"success":true,"data":{},"code":null,"message":null}
```

业务错误统一返回 `success=false`、稳定的 `code` 和可读 `message`。未登录返回 401；登录但无接口权限返回 403。响应头 `X-Request-Id` 可与应用日志关联，调用方传入同名 Header 时服务会沿用它。

## 主要资源

| 资源 | 路径 | 说明 |
|---|---|---|
| 登录与当前用户 | `/api/auth/*` | token、权限码与数据范围 |
| 项目 / 产品 | `/api/projects`、`/api/products` | 基础资料 |
| 设备 | `/api/devices` | 台账查询和新增 |
| 分组 / 标签 | `/api/device-taxonomy/*` | 创建及批量维护设备关系 |
| 导入 | `/api/device-imports` | 上传、任务、错误明细、CSV 下载 |
| 工单 | `/api/work-orders` | 多设备工单、分页、轨迹和状态迁移 |
| 告警与恢复 | `/api/alarms`、`/api/alarm-events/*` | 结果查询、修正确认和重放 |
| 巡检与补偿 | `/api/timeout-inspections/*` | 手工执行、任务结果、失败补偿 |
| RBAC | `/api/rbac/*` | 用户、角色、权限和数据范围 |
| 审计 | `/api/audit-logs` | 关键管理与业务操作 |
| 总览 | `/api/dashboard/summary` | 按数据范围汇总 |

分页接口使用从 1 开始的 `page` 和最大 100 的 `size`。具体字段、校验约束和状态值以 OpenAPI 页面为准。

## RocketMQ 消息契约

Topic：`iot-device-alarm`，JSON 示例：

```json
{
  "eventId": "gateway-a-20260915-0001",
  "deviceSn": "DEMO-SN-001",
  "eventType": "DEVICE_OFFLINE",
  "severity": "HIGH",
  "occurredAt": "2026-09-15T12:00:00",
  "data": {"source": "gateway-a"}
}
```

`eventId` 是业务幂等键。业务坏消息会被记录并确认消费；数据库等系统异常会记录失败并抛出，触发 RocketMQ 重试。
