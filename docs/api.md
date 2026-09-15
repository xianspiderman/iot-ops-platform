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

## 管理端、OpenAPI 与 XXL-JOB 串联演示

管理端是业务操作入口；OpenAPI 是同一后端接口的查看、调试和独立验证入口；XXL-JOB 是任务调度控制端，按周期或手动触发后端注册的 `workOrderTimeoutInspection`。三者最终复用同一个后端业务 Service 和 MySQL 数据。

正常链路：

1. 在管理端“工单管理”新建一张关联设备 1 的工单。为缩短本机演示等待，把最新非终态工单准备为已到期：

   ```powershell
   docker compose exec -T mysql mysql -uiot_ops -piot_ops_dev iot_ops -e "UPDATE work_order SET deadline_time=DATE_SUB(NOW(3), INTERVAL 1 MINUTE), timeout_flag=0, timeout_time=NULL WHERE status NOT IN ('CLOSED','CANCELED') ORDER BY id DESC LIMIT 1;"
   ```

2. 在工单“详情与轨迹”中确认到期时间已过去、超时状态仍为“未超时”。
3. 登录 `http://localhost:8088`，在任务管理找到 `workOrderTimeoutInspection` 并执行一次。
4. 回到管理端“可靠性中心 → 巡检执行记录”，预期出现来源为“XXL-JOB”的成功记录；再查看工单详情，预期出现超时时间和操作方为“系统”的“标记超时”轨迹。
5. 在 OpenAPI 携带登录响应给出的 token Header，调用 `GET /api/work-orders/{id}` 和 `GET /api/timeout-inspections/executions`，独立核对 `timeoutFlag`、`timeoutTime`、SYSTEM 轨迹与任务计数。

失败与人工补偿只在本机演示环境执行。先准备另一张到期工单，再临时阻止轨迹写入：

```powershell
docker compose exec -T mysql mysql -uroot -proot_dev_only iot_ops -e "DROP TRIGGER IF EXISTS demo_reject_timeout_track; CREATE TRIGGER demo_reject_timeout_track BEFORE INSERT ON work_order_track FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='demo timeout track failure';"
```

在“可靠性中心”手动执行巡检后，执行记录应为部分失败，失败页出现工单、原因和次数，该工单仍未标记超时。随即删除演示 trigger：

```powershell
docker compose exec -T mysql mysql -uroot -proot_dev_only iot_ops -e "DROP TRIGGER IF EXISTS demo_reject_timeout_track;"
```

刷新失败记录并点击“人工补偿”。预期失败状态变为“已解决”，工单出现超时标记和补偿轨迹；OpenAPI 返回应与页面一致。演示结束必须确认 trigger 已删除。

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
