# 测试与验收报告

最后更新：2026-09-15。本文件只记录实际执行过的验证；未运行的方案不写为结论。

## 环境

| 项目 | 实际环境 |
|---|---|
| 主机 | Windows，Docker Desktop Linux containers |
| Docker Engine | 29.6.1，分配内存 15,876 MB |
| 本地 JDK | 25.0.1，Maven `release=21` |
| CI JDK | GitHub Actions Temurin 21 |
| Maven | Wrapper 3.9.9 |
| 集成数据库 | Testcontainers `mysql:8.4.11` |
| 集成缓存 | Testcontainers `redis:8.2.1-alpine` |
| 完整部署 | MySQL 8.4.11、Redis 8.2.1、RocketMQ 5.3.2、XXL-JOB 3.4.2、Java 21 JRE、Node 24 Alpine |

本机只有 JDK 25，因此宿主机测试由 JDK 25 编译到 Java 21；最终容器使用 Java 21 JRE 运行，以覆盖发布基线。涉及 MySQL 唯一索引、事务、锁和并发的断言全部在真实 MySQL 上执行。

## 自动化测试入口

```powershell
.\mvnw.cmd -B verify
```

结果：`BUILD SUCCESS`。9 个单元测试全部通过；16 个常规 MySQL/Redis 集成测试全部通过；2 个性能方法默认跳过。可选性能方法不影响常规套件结果。

前端：

```powershell
cd frontend
npm ci
npm run build
npm audit --audit-level=high
```

结果：TypeScript 类型检查和 Vite 生产构建通过，`npm audit` 为 0 个漏洞。Element Plus 全量样式使入口 chunk 超过 Vite 500 kB 提示阈值；这不是构建失败，后续只有在实际首屏指标成为问题时才继续按组件拆分。

## 覆盖矩阵

| 验收点 | 自动化证据 |
|---|---|
| 后端单元测试 | 工单服务、状态机、视图与密码哈希序列化、RocketMQ 监听异常语义 |
| MySQL 真实行为 | V1—V5 Flyway 在 MySQL 8.4.11 空库执行；所有 IT 使用该库 |
| 工单状态机 | 正常 `WAITING -> PROCESSING -> WAIT_VERIFY -> CLOSED` 与非法路径 |
| 两用户并发接单 | 同一 latch 释放两线程，恰好一个成功且只有一条 ACCEPT 轨迹 |
| 工单与轨迹回滚 | MySQL trigger 注入轨迹失败，主表状态保持旧值 |
| 一对多分页 | 两张多设备工单、页大小 1，总数和两页关系均完整 |
| 导入正常与混合错误 | 全合法、原始行号、必填/格式、文件内重复、库内重复 |
| 导入中间批次异常 | INSERT 批量大小 2，在第二批触发唯一冲突，第一批一起回滚，任务 FAILED |
| XXL-JOB 重复执行 | 相同过期工单第二次不再成为候选，只有一条 SYSTEM 轨迹 |
| 巡检单条回滚/恢复 | 一条轨迹失败只回滚该工单；其他工单提交；移除故障后重跑解决 failure |
| 巡检人工补偿 | pending failure 复用单条事务处理并改为 RESOLVED |
| 告警首次/重复 | 同 eventId 两次处理，只有一条 alarm，第二次 DUPLICATE |
| 告警并发 | 两线程同 eventId，唯一约束仲裁为 PROCESSED + DUPLICATE |
| 业务坏消息 | 未知 SN 保存 BUSINESS_BAD_MESSAGE；修正保持 eventId 并成功重放 |
| 告警系统异常 | trigger 注入落库失败，记录 SYSTEM_FAILURE；去除故障后同 eventId 成功 |
| 权限缓存 | 第一次 miss、第二次 hit；修改角色权限后缓存失效并重新加载 |
| 会话变化 | 角色权限变化后，受影响用户既有 token 查询不到登录 ID |
| 数据权限 | 项目运维员分页只见项目 1，读取/写入项目 2 被拒绝 |
| 500 条 XML 多值 INSERT | 真实提交、每轮 500 行、一次 Mapper/SQL 调用、500 个 values 元组、唯一约束与整事务回滚、测试后零残留 |

异常注入使用测试期 MySQL trigger，并在每个测试前后删除，不进入 Flyway 或运行环境。日志中的预期异常栈用于证明回滚路径被真正执行，不代表测试失败。

## 导入查询阶段基准

运行命令：

```powershell
.\mvnw.cmd -B "-Diot.benchmark=true" "-Dit.test=MySqlBusinessFlowIT#benchmarkImportLookupRoundTripsAgainstRowByRowCandidate" verify
```

方法：在同一个 Spring 测试进程和同一个 MySQL 8.4.11 Testcontainer 中构造 1,000 个不存在的 SN；先执行 20 次预热查询。第一种候选方案逐行查询项目、产品和 SN，共 3,000 次 SQL；当前方案查询项目和产品各一次，SN 按 500 个一批查询两次，共 4 次 SQL。两条路径验证得到相同的“已有 SN 数量”。

v1.0.0 记录的实际输出：

```text
IMPORT_LOOKUP_BENCHMARK rows=1000 naiveQueries=3000 naiveMs=1924 batchQueries=4 batchMs=6
```

v1.1.0 发布前按同一方法重新执行，输出为：

```text
IMPORT_LOOKUP_BENCHMARK rows=1000 naiveQueries=3000 naiveMs=2001 batchQueries=4 batchMs=6
```

可得出的结论仅是：在这次受控环境中，集中收集业务键并批量查询显著减少了该阶段 SQL 往返，并缩短了测得时间。不能从这个数字推断：

- 完整 Excel 解析和插入能达到同样倍数；
- 生产网络、数据分布和缓冲池下仍是 6 ms；
- 10,000 行的堆占用已经通过压力测试；
- 应立即把同步导入扩到更大文件。

批量写入正确性由中间批次异常回滚测试覆盖；此次基准不测写入吞吐。原来的 `6 ms` 保持原含义：它只表示 4 次批量查询在该次运行中的应用侧耗时，不能与下面的 INSERT 指标相加。

## 500 条 XML 多值 INSERT 基准

运行命令：

```powershell
.\mvnw.cmd -B "-Diot.benchmark=true" "-Dit.test=MySqlBusinessFlowIT#benchmarkFiveHundredDeviceXmlMultiValueInsert" verify
```

### 数据和计时边界

- 继续使用本文件“环境”中的同一套 Testcontainers：真实 `mysql:8.4.11`、实际 `DeviceMapper.batchInsert` 与 `mapper/device/DeviceMapper.xml`，没有使用 H2、Mock 或只生成不执行的 SQL。
- 每轮在计时前构造 500 个完整 `Device` 对象；SN 带本次运行与轮次前缀，确保每轮唯一。项目 1、产品 1 和 Flyway 数据在计时前确认存在。
- Excel 解析、实体构造、批量查询、业务校验、Set/Map 分类、插入后的计数查询和清理都在两个计时区间之外。
- **应用侧观测的批量 INSERT 调用耗时**：在事务已经开启后，从调用 `DeviceMapper.batchInsert(devices)` 前开始，到 Mapper 返回后结束。它包含 MyBatis 参数绑定、JDBC 往返和数据库执行等待，但明确不包含事务提交；它不是 MySQL 内部纯执行耗时。
- **事务总耗时**：从调用 Spring `PlatformTransactionManager.getTransaction` 开始，到 `commit` 成功返回后结束，包含开启事务、同一次 INSERT 和提交。
- 先执行 2 轮预热，再执行 10 轮正式测量。所有轮次真实提交，不用测试回滚代替 commit；结果校验和最终 `DELETE` 清理均不进入计时。
- 测试期 MyBatis `Executor.update` 探针读取实际 BoundSql。每轮断言 Mapper 调用数为 1、SQL 语句数为 1、SQL 以 `INSERT INTO device` 开始且包含 500 个 `(?...)` values 元组，排除逐行执行 500 次 INSERT。
- 正式轮结束后另开事务：先插入一个合法设备，再执行含重复 SN 的批量 INSERT；唯一约束拒绝第二条语句后显式回滚，并断言第一条也未留下，用于确认唯一约束和事务仍生效。最后按本次运行前缀清理并断言零残留。

### 原始样本

预热数据不进入统计：

| 预热轮次 | INSERT 调用 ms | 事务总耗时 ms |
|---:|---:|---:|
| 1 | 99.304 | 110.263 |
| 2 | 81.582 | 91.436 |

10 轮正式样本：

| 轮次 | Mapper 调用 | SQL 数 | values 组数 | INSERT 调用 ms | 事务总耗时 ms |
|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 1 | 500 | 67.939 | 77.204 |
| 2 | 1 | 1 | 500 | 59.979 | 68.781 |
| 3 | 1 | 1 | 500 | 58.121 | 65.397 |
| 4 | 1 | 1 | 500 | 54.712 | 62.481 |
| 5 | 1 | 1 | 500 | 42.551 | 49.260 |
| 6 | 1 | 1 | 500 | 38.636 | 45.912 |
| 7 | 1 | 1 | 500 | 46.882 | 59.407 |
| 8 | 1 | 1 | 500 | 17.252 | 23.971 |
| 9 | 1 | 1 | 500 | 26.224 | 33.829 |
| 10 | 1 | 1 | 500 | 29.981 | 49.812 |

统计使用全部 10 个正式样本；中位数为排序后第 5、6 项平均值，P95 使用 nearest-rank（10 个样本时为最大值）：

| 指标 | 最小值 ms | 中位数 ms | 平均值 ms | P95 ms |
|---|---:|---:|---:|---:|
| 应用侧观测的批量 INSERT 调用耗时 | 17.252 | 44.716 | 44.228 | 67.939 |
| 包含 commit 的事务总耗时 | 23.971 | 54.609 | 53.605 | 77.204 |

### 可以和不可以得出的结论

本次结果证明：在上述本机环境中，500 条合法设备确实通过一次 Mapper 调用和一条 XML 多值 INSERT 写入并提交；应用侧观测的调用耗时中位数为 `44.716 ms`，包含 commit 的事务总耗时中位数为 `54.609 ms`。事务总耗时与 INSERT 调用耗时必须分开报告。

这些数字不包含 Excel 解析、批量查询、业务校验、Set/Map 分类和完整导入任务状态写入，因此不能称为“500 条完整导入耗时”；也不能与查询基准的 `6 ms` 相加后声称是完整导入耗时。单次本机样本不代表生产 MySQL 的内部纯执行时间或 SLA。

## 完整 Compose 验收

执行 `scripts/start.ps1` 等价流程后，实际观察：

- MySQL 和 Redis healthy；RocketMQ NameServer/Broker、XXL-JOB Admin 正常运行。
- 后端在 Java 21 JRE 启动，Flyway 验证 V1—V5，RocketMQ listener 启动，XXL handler 注册，健康接口返回 `UP`。
- 前端 Node 24 静态服务 healthy，`http://localhost:8080` 返回 200。
- XXL-JOB 执行器注册地址为 `http://backend:9999`；预置五分钟任务触发码与处理码均为 200，并在业务库留下 `trigger_source=XXL_JOB, status=SUCCESS` 的 execution。
- 使用 `mqadmin sendMessage` 发布真实消息后，事件成为 PROCESSED 并生成 alarm；同 eventId 发布两次只生成一条 alarm。
- 未知设备消息成为 BUSINESS_BAD_MESSAGE；通过 HTTP 确认修正并重放后，原记录变为 PROCESSED、attempt count 为 2，且绑定一条 alarm。
- `admin` 登录可获取 16 个权限并访问 RBAC；`operator` 数据范围只有项目 1，访问 RBAC 返回 HTTP 403。
- 中文管理端八个业务路由均由隐藏 Edge 直接加载并验证页面标题；直接刷新受保护路由也会先恢复会话再加载业务数据。工单详情抽屉实际显示“系统 / 标记超时”轨迹，可靠性中心实际显示“已解决 / 演示用操作轨迹写入失败”。登录页和总览截图由运行中的 v1.1.0 前端重新采集。
- 三入口串联实测：管理端创建并准备到期工单后，XXL-JOB 手动登录与触发均返回 200，调度日志 `trigger_code=200, handle_code=200`；工单由 `timeoutFlag=false` 变为 true，留下 `SYSTEM:TIMEOUT_MARK`，执行记录为 `XXL_JOB/SUCCESS/scanned=1/success=1`；OpenAPI 的 X-Token 安全方案和两个查询路径返回同一结果。
- 失败补偿实测：临时 trigger 注入轨迹失败后，任务为 PARTIAL_FAILED、失败数 1、工单超时字段回滚、failure 为 PENDING；删除 trigger 后人工补偿使 failure 变为 RESOLVED，并留下 `USER:TIMEOUT_MARK`。演示 trigger 最终数量为 0。

Compose 首轮还暴露了两项已修复问题：Docker 新建 RocketMQ 命名卷为 root 所有导致 Broker 无法写入；XXL executor 日志目录对非 root 后端不可写。最终配置使用一次性卷权限初始化容器，并把执行日志置于 `/tmp/xxl-job`。这也是完整启动验证不能只停留在 `docker compose config` 或镜像构建的原因。

v1.1.0 空卷复验还发现 Dockerfile 曾把运行包名固定为 `1.0.0`：本地残留旧 JAR 会让镜像表面构建成功却运行旧代码，干净 CI 则找不到文件。启动脚本和容器 CI 现先执行 Maven `clean package`，Dockerfile 只接收清理后唯一的 `iot-ops-platform-*.jar`；复验确认 target 和镜像中实际运行的包为 1.1.0。

## GitHub Actions

`.github/workflows/ci.yml` 在 push、Tag 与 Pull Request 运行三个独立 job：

1. Temurin 21 下执行 Maven `verify`，包含 Testcontainers 集成测试；
2. Node 24 下执行 `npm ci`、`npm audit --audit-level=high` 和生产构建；
3. 打包后端、校验 Compose 模型并构建后端/前端镜像。

发布前以远程工作流实际绿色结果作为最终门禁；若远程运行与本机结果不同，先修复再创建 GitHub Release。

## 可重复性和限制

- 性能时间受宿主负载、Docker 文件系统、JIT 与 MySQL 缓存影响，应关注查询/SQL 次数、全部原始样本和趋势，而不是把单次最好值当 SLA。
- RocketMQ E2E 使用真实 Broker；自动测试把业务处理器直接并发调用，以稳定注入异常并断言数据库结果。
- XXL-JOB E2E 使用真实 Admin/Executor；自动测试直接调用巡检协调器覆盖逐条事务和恢复语义。
- 没有用当前样本推导容量上限。异步导入、任务分片、表分区等变化必须先出现可复现的瓶颈与验收指标。
