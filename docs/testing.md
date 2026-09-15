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

结果：`BUILD SUCCESS`。9 个单元测试全部通过；16 个常规 MySQL/Redis 集成测试全部通过；性能方法 1 个默认跳过。可选性能方法不影响常规套件结果。

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
| MySQL 真实行为 | V1—V4 Flyway 在 MySQL 8.4.11 空库执行；所有 IT 使用该库 |
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

异常注入使用测试期 MySQL trigger，并在每个测试前后删除，不进入 Flyway 或运行环境。日志中的预期异常栈用于证明回滚路径被真正执行，不代表测试失败。

## 导入查询阶段基准

运行命令：

```powershell
.\mvnw.cmd -B "-Diot.benchmark=true" "-Dit.test=MySqlBusinessFlowIT#benchmarkImportLookupRoundTripsAgainstRowByRowCandidate" verify
```

方法：在同一个 Spring 测试进程和同一个 MySQL 8.4.11 Testcontainer 中构造 1,000 个不存在的 SN；先执行 20 次预热查询。第一种候选方案逐行查询项目、产品和 SN，共 3,000 次 SQL；当前方案查询项目和产品各一次，SN 按 500 个一批查询两次，共 4 次 SQL。两条路径验证得到相同的“已有 SN 数量”。

一次实际输出：

```text
IMPORT_LOOKUP_BENCHMARK rows=1000 naiveQueries=3000 naiveMs=1924 batchQueries=4 batchMs=6
```

可得出的结论仅是：在这次受控环境中，集中收集业务键并批量查询显著减少了该阶段 SQL 往返，并缩短了测得时间。不能从这个数字推断：

- 完整 Excel 解析和插入能达到同样倍数；
- 生产网络、数据分布和缓冲池下仍是 6 ms；
- 10,000 行的堆占用已经通过压力测试；
- 应立即把同步导入扩到更大文件。

批量写入正确性由中间批次异常回滚测试覆盖；此次基准不测写入吞吐。

## 完整 Compose 验收

执行 `scripts/start.ps1` 等价流程后，实际观察：

- MySQL 和 Redis healthy；RocketMQ NameServer/Broker、XXL-JOB Admin 正常运行。
- 后端在 Java 21 JRE 启动，Flyway 验证 V1—V4，RocketMQ listener 启动，XXL handler 注册，健康接口返回 `UP`。
- 前端 Node 24 静态服务 healthy，`http://localhost:8080` 返回 200。
- XXL-JOB 执行器注册地址为 `http://backend:9999`；预置五分钟任务触发码与处理码均为 200，并在业务库留下 `trigger_source=XXL_JOB, status=SUCCESS` 的 execution。
- 使用 `mqadmin sendMessage` 发布真实消息后，事件成为 PROCESSED 并生成 alarm；同 eventId 发布两次只生成一条 alarm。
- 未知设备消息成为 BUSINESS_BAD_MESSAGE；通过 HTTP 确认修正并重放后，原记录变为 PROCESSED、attempt count 为 2，且绑定一条 alarm。
- `admin` 登录可获取 16 个权限并访问 RBAC；`operator` 数据范围只有项目 1，访问 RBAC 返回 HTTP 403。

Compose 首轮还暴露了两项已修复问题：Docker 新建 RocketMQ 命名卷为 root 所有导致 Broker 无法写入；XXL executor 日志目录对非 root 后端不可写。最终配置使用一次性卷权限初始化容器，并把执行日志置于 `/tmp/xxl-job`。这也是完整启动验证不能只停留在 `docker compose config` 或镜像构建的原因。

## GitHub Actions

`.github/workflows/ci.yml` 在 push、Tag 与 Pull Request 运行三个独立 job：

1. Temurin 21 下执行 Maven `verify`，包含 Testcontainers 集成测试；
2. Node 24 下执行 `npm ci` 和生产构建；
3. 打包后端、校验 Compose 模型并构建后端/前端镜像。

发布前以远程工作流实际绿色结果作为最终门禁；若远程运行与本机结果不同，先修复再创建 GitHub Release。

## 可重复性和限制

- 性能时间受宿主负载、Docker 文件系统、JIT 与 MySQL 缓存影响，应关注查询次数和趋势而不是把单次数值当 SLA。
- RocketMQ E2E 使用真实 Broker；自动测试把业务处理器直接并发调用，以稳定注入异常并断言数据库结果。
- XXL-JOB E2E 使用真实 Admin/Executor；自动测试直接调用巡检协调器覆盖逐条事务和恢复语义。
- 没有用当前样本推导容量上限。异步导入、任务分片、表分区等变化必须先出现可复现的瓶颈与验收指标。
