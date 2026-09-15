package io.github.xianspiderman.iotops.integration;

import com.alibaba.excel.EasyExcel;
import cn.dev33.satoken.stp.StpUtil;
import io.github.xianspiderman.iotops.alarm.AlarmEventProcessor;
import io.github.xianspiderman.iotops.alarm.AlarmProcessOutcome;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.auth.PermissionCacheService;
import io.github.xianspiderman.iotops.auth.RbacService;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import io.github.xianspiderman.iotops.device.DeviceService;
import io.github.xianspiderman.iotops.device.importer.DeviceImportError;
import io.github.xianspiderman.iotops.device.importer.DeviceImportRow;
import io.github.xianspiderman.iotops.device.importer.DeviceImportService;
import io.github.xianspiderman.iotops.device.importer.DeviceImportStatus;
import io.github.xianspiderman.iotops.device.importer.DeviceImportTask;
import io.github.xianspiderman.iotops.device.importer.DeviceImportTaskStateService;
import io.github.xianspiderman.iotops.device.importer.DeviceImportWriteService;
import io.github.xianspiderman.iotops.workorder.WorkOrder;
import io.github.xianspiderman.iotops.workorder.WorkOrderService;
import io.github.xianspiderman.iotops.workorder.WorkOrderStatus;
import io.github.xianspiderman.iotops.workorder.WorkOrderView;
import io.github.xianspiderman.iotops.timeout.TimeoutFailure;
import io.github.xianspiderman.iotops.timeout.TimeoutFailureMapper;
import io.github.xianspiderman.iotops.timeout.TimeoutInspectionService;
import io.github.xianspiderman.iotops.timeout.TimeoutJobExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.data.redis.repositories.enabled=false",
        "spring.data.redis.client-type=jedis",
        "iot-ops.import.insert-batch-size=2"
})
class MySqlBusinessFlowIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("iot_ops_test")
            .withUsername("iot_ops")
            .withPassword("iot_ops_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WorkOrderService workOrderService;
    @Autowired
    private DeviceImportService importService;
    @Autowired
    private DeviceImportTaskStateService taskStateService;
    @Autowired
    private DeviceImportWriteService importWriteService;
    @Autowired
    private AlarmEventProcessor alarmEventProcessor;
    @Autowired
    private TimeoutInspectionService timeoutInspectionService;
    @Autowired
    private TimeoutFailureMapper timeoutFailureMapper;
    @Autowired
    private PermissionCacheService permissionCacheService;
    @Autowired
    private RbacService rbacService;
    @Autowired
    private DataScopeService dataScopeService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private BatchInsertStatementProbe batchInsertProbe;

    @BeforeEach
    void cleanBusinessRows() {
        try {
            REDIS.execInContainer("redis-cli", "FLUSHALL");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        jdbc.execute("DROP TRIGGER IF EXISTS reject_submit_track");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_alarm_insert");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_timeout_track");
        jdbc.update("DELETE FROM alarm_event_record");
        jdbc.update("DELETE FROM alarm");
        jdbc.update("DELETE FROM business_audit_log");
        jdbc.update("DELETE FROM timeout_failure");
        jdbc.update("DELETE FROM timeout_job_execution");
        jdbc.update("DELETE FROM work_order_track");
        jdbc.update("DELETE FROM work_order_device");
        jdbc.update("DELETE FROM work_order");
        jdbc.update("DELETE FROM device_import_error");
        jdbc.update("DELETE FROM device_import_task");
        jdbc.update("DELETE FROM device WHERE id > 2");
        jdbc.update("DELETE FROM project WHERE id > 1");
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id = 2");
        jdbc.update("""
                INSERT INTO sys_role_permission(role_id, permission_id)
                SELECT 2, id FROM sys_permission
                 WHERE permission_code IN ('project:read', 'product:read', 'device:read', 'device:write',
                                           'device:import', 'work-order:read', 'work-order:write',
                                           'alarm:read', 'timeout:read')
                """);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 4");
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (4, 2)");
        jdbc.update("DELETE FROM sys_user_data_scope WHERE user_id = 4");
        jdbc.update("INSERT INTO sys_user_data_scope(user_id, scope_type, project_id) VALUES (4, 'PROJECT', 1)");
        jdbc.update("""
                INSERT INTO sys_user(id, username, password_hash, display_name, status)
                VALUES (2, 'operator-a', 'unused', 'Operator A', 'ENABLED'),
                       (3, 'operator-b', 'unused', 'Operator B', 'ENABLED')
                ON DUPLICATE KEY UPDATE status = VALUES(status)
                """);
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_submit_track");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_alarm_insert");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_timeout_track");
        StpUtil.logout(2L);
        StpUtil.logout(4L);
    }

    @Test
    void onlyOneOfTwoConcurrentOperatorsCanAccept() throws Exception {
        WorkOrder order = createOrder(List.of(1L));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(acceptAttempt(order.getId(), 2L, ready, go));
            Future<Boolean> second = executor.submit(acceptAttempt(order.getId(), 3L, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM work_order WHERE id = ?", String.class, order.getId()))
                .isEqualTo(WorkOrderStatus.PROCESSING.name());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_order_track WHERE work_order_id = ? AND action = 'ACCEPT'",
                Integer.class, order.getId())).isEqualTo(1);
    }

    @Test
    void completesTheMainStateMachineAndPersistsEveryTrack() {
        WorkOrder order = createOrder(List.of(1L));
        workOrderService.accept(order.getId(), 2L);
        workOrderService.submit(order.getId(), 2L, "Replaced the radio module");
        workOrderService.verifyAndClose(order.getId(), 3L, "Connectivity verified");

        assertThat(jdbc.queryForObject("SELECT status FROM work_order WHERE id = ?", String.class, order.getId()))
                .isEqualTo(WorkOrderStatus.CLOSED.name());
        assertThat(jdbc.queryForList(
                "SELECT action FROM work_order_track WHERE work_order_id = ? ORDER BY id", String.class, order.getId()))
                .containsExactly("CREATE", "ACCEPT", "SUBMIT", "VERIFY_CLOSE");
    }

    @Test
    void workOrderUpdateRollsBackWhenTrackInsertFails() {
        WorkOrder order = createOrder(List.of(1L));
        workOrderService.accept(order.getId(), 2L);
        jdbc.execute("""
                CREATE TRIGGER reject_submit_track BEFORE INSERT ON work_order_track
                FOR EACH ROW
                BEGIN
                  IF NEW.action = 'SUBMIT' THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected track failure';
                  END IF;
                END
                """);

        assertThatThrownBy(() -> workOrderService.submit(order.getId(), 2L, "Replaced the radio module"))
                .isInstanceOf(RuntimeException.class);

        WorkOrder after = jdbc.queryForObject(
                "SELECT id, status, solution FROM work_order WHERE id = ?",
                (rs, rowNum) -> {
                    WorkOrder value = new WorkOrder();
                    value.setId(rs.getLong("id"));
                    value.setStatus(rs.getString("status"));
                    value.setSolution(rs.getString("solution"));
                    return value;
                }, order.getId());
        assertThat(after.getStatus()).isEqualTo(WorkOrderStatus.PROCESSING.name());
        assertThat(after.getSolution()).isNull();
    }

    @Test
    void parentPageIsStableWhenOneOrderHasSeveralDevices() {
        createOrder(List.of(1L, 2L));
        createOrder(List.of(1L));

        PageResult<WorkOrderView> firstPage = workOrderService.page(1, 1, null);
        PageResult<WorkOrderView> secondPage = workOrderService.page(2, 1, null);

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.records()).hasSize(1);
        assertThat(secondPage.records()).hasSize(1);
        assertThat(firstPage.records().getFirst().workOrder().getId())
                .isNotEqualTo(secondPage.records().getFirst().workOrder().getId());
        assertThat(firstPage.records().getFirst().deviceIds()).hasSize(1);
        assertThat(secondPage.records().getFirst().deviceIds()).containsExactly(1L, 2L);
    }

    @Test
    void importSeparatesValidRowsFromDetailedBusinessErrors() {
        List<DeviceImportRow> rows = List.of(
                row("BATCH-SN-001", "West sensor", "SMART-CAMPUS", "ENV-SENSOR", "123456789012345"),
                row("BATCH-SN-001", "Duplicate", "SMART-CAMPUS", "ENV-SENSOR", null),
                row("DEMO-SN-001", "Existing", "SMART-CAMPUS", "ENV-SENSOR", null),
                row("BATCH-SN-002", null, "SMART-CAMPUS", "ENV-SENSOR", null),
                row("BATCH-SN-003", "East sensor", "SMART-CAMPUS", "ENV-SENSOR", null));

        DeviceImportTask task = importService.importWorkbook("mixed.xlsx",
                new ByteArrayInputStream(workbook(rows)), 1L);

        assertThat(task.getStatus()).isEqualTo(DeviceImportStatus.PARTIAL_FAILED.name());
        assertThat(task.getTotalRows()).isEqualTo(5);
        assertThat(task.getSuccessRows()).isEqualTo(2);
        assertThat(task.getErrorRows()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE sn LIKE 'BATCH-SN-%'", Integer.class))
                .isEqualTo(2);
        List<DeviceImportError> errors = importService.errors(task.getId());
        assertThat(errors).extracting(DeviceImportError::getRowNo).containsExactly(3, 4, 5);
        assertThat(errors).extracting(DeviceImportError::getErrorMessage)
                .anyMatch(message -> message.contains("duplicated in this file"))
                .anyMatch(message -> message.contains("already exists"))
                .anyMatch(message -> message.contains("Device name is required"));
    }

    @Test
    void importsAnEntirelyValidWorkbook() {
        List<DeviceImportRow> rows = List.of(
                row("VALID-SN-001", "Valid one", "SMART-CAMPUS", "ENV-SENSOR", null),
                row("VALID-SN-002", "Valid two", "SMART-CAMPUS", "ENV-SENSOR", "123456789012345"));

        DeviceImportTask task = importService.importWorkbook("valid.xlsx",
                new ByteArrayInputStream(workbook(rows)), 1L);

        assertThat(task.getStatus()).isEqualTo(DeviceImportStatus.SUCCESS.name());
        assertThat(task.getTotalRows()).isEqualTo(2);
        assertThat(task.getSuccessRows()).isEqualTo(2);
        assertThat(task.getErrorRows()).isZero();
        assertThat(importService.errors(task.getId())).isEmpty();
    }

    @Test
    void uniqueFailureInLaterInsertBatchRollsBackEarlierBatch() {
        DeviceImportTask task = taskStateService.start("rollback.xlsx", 1L);
        List<Device> devices = new ArrayList<>();
        devices.add(device("ROLLBACK-SN-001"));
        devices.add(device("ROLLBACK-SN-002"));
        devices.add(device("ROLLBACK-SN-001"));

        assertThatThrownBy(() -> importWriteService.persist(task.getId(), 3, devices, List.of()))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE sn LIKE 'ROLLBACK-SN-%'", Integer.class))
                .isZero();
        assertThat(importService.getTask(task.getId()).getStatus()).isEqualTo(DeviceImportStatus.PROCESSING.name());
        taskStateService.fail(task.getId(), "Injected second-batch unique conflict");
        assertThat(importService.getTask(task.getId()).getStatus()).isEqualTo(DeviceImportStatus.FAILED.name());
    }

    @Test
    void firstAndDuplicateAlarmMessagesCreateOnlyOneAlarm() {
        String payload = alarmJson("alarm-first", "DEMO-SN-001", "DEVICE_OFFLINE");

        assertThat(alarmEventProcessor.processRaw(payload)).isEqualTo(AlarmProcessOutcome.PROCESSED);
        assertThat(alarmEventProcessor.processRaw(payload)).isEqualTo(AlarmProcessOutcome.DUPLICATE);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alarm WHERE event_id = 'alarm-first'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT process_status FROM alarm_event_record WHERE event_id = 'alarm-first'", String.class))
                .isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT online_status FROM device WHERE sn = 'DEMO-SN-001'", String.class))
                .isEqualTo("OFFLINE");
    }

    @Test
    void concurrentAlarmDeliveryIsIdempotentAtTheDatabaseBoundary() throws Exception {
        String payload = alarmJson("alarm-concurrent", "DEMO-SN-001", "LOW_BATTERY");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<AlarmProcessOutcome> attempt = () -> {
                ready.countDown();
                go.await();
                return alarmEventProcessor.processRaw(payload);
            };
            Future<AlarmProcessOutcome> first = executor.submit(attempt);
            Future<AlarmProcessOutcome> second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(AlarmProcessOutcome.PROCESSED, AlarmProcessOutcome.DUPLICATE);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM alarm WHERE event_id = 'alarm-concurrent'", Integer.class)).isEqualTo(1);
    }

    @Test
    void badAlarmCanBeCorrectedConfirmedAndReprocessedOnce() {
        String invalid = alarmJson("alarm-corrected", "UNKNOWN-SN", "HIGH_TEMPERATURE");
        assertThat(alarmEventProcessor.processRaw(invalid)).isEqualTo(AlarmProcessOutcome.BUSINESS_BAD_MESSAGE);
        Long recordId = jdbc.queryForObject(
                "SELECT id FROM alarm_event_record WHERE event_id = 'alarm-corrected'", Long.class);
        assertThat(jdbc.queryForObject(
                "SELECT process_status FROM alarm_event_record WHERE id = ?", String.class, recordId))
                .isEqualTo("BUSINESS_BAD_MESSAGE");

        alarmEventProcessor.confirm(recordId,
                alarmJson("alarm-corrected", "DEMO-SN-001", "HIGH_TEMPERATURE"), 1L);
        assertThat(alarmEventProcessor.reprocess(recordId)).isEqualTo(AlarmProcessOutcome.PROCESSED);
        assertThat(alarmEventProcessor.reprocess(recordId)).isEqualTo(AlarmProcessOutcome.DUPLICATE);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM alarm WHERE event_id = 'alarm-corrected'", Integer.class)).isEqualTo(1);
    }

    @Test
    void systemAlarmFailureIsRecordedThrownAndCanSucceedOnRetry() {
        jdbc.execute("""
                CREATE TRIGGER reject_alarm_insert BEFORE INSERT ON alarm
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected alarm storage failure'
                """);
        String payload = alarmJson("alarm-retry", "DEMO-SN-001", "LOW_BATTERY");

        assertThatThrownBy(() -> alarmEventProcessor.processRaw(payload)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject(
                "SELECT process_status FROM alarm_event_record WHERE event_id = 'alarm-retry'", String.class))
                .isEqualTo("SYSTEM_FAILURE");

        jdbc.execute("DROP TRIGGER reject_alarm_insert");
        assertThat(alarmEventProcessor.processRaw(payload)).isEqualTo(AlarmProcessOutcome.PROCESSED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alarm WHERE event_id = 'alarm-retry'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void timeoutInspectionIsSafeToRepeat() {
        WorkOrder order = createOrder(List.of(1L));
        expire(order.getId());

        TimeoutJobExecution first = timeoutInspectionService.run("TEST");
        TimeoutJobExecution second = timeoutInspectionService.run("TEST");

        assertThat(first.getScannedCount()).isEqualTo(1);
        assertThat(first.getSuccessCount()).isEqualTo(1);
        assertThat(second.getScannedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT timeout_flag FROM work_order WHERE id = ?", Boolean.class,
                order.getId())).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_order_track WHERE work_order_id = ? AND action = 'TIMEOUT_MARK'",
                Integer.class, order.getId())).isEqualTo(1);
    }

    @Test
    void oneTimeoutFailureRollsBackOnlyThatOrderAndRecoveryRunResolvesIt() {
        WorkOrder failing = createOrder(List.of(1L));
        WorkOrder healthy = createOrder(List.of(2L));
        expire(failing.getId());
        expire(healthy.getId());
        jdbc.execute("""
                CREATE TRIGGER reject_timeout_track BEFORE INSERT ON work_order_track
                FOR EACH ROW
                BEGIN
                  IF NEW.action = 'TIMEOUT_MARK' AND NEW.work_order_id = %d THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected timeout track failure';
                  END IF;
                END
                """.formatted(failing.getId()));

        TimeoutJobExecution first = timeoutInspectionService.run("TEST");
        assertThat(first.getSuccessCount()).isEqualTo(1);
        assertThat(first.getFailureCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT timeout_flag FROM work_order WHERE id = ?", Boolean.class,
                failing.getId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT timeout_flag FROM work_order WHERE id = ?", Boolean.class,
                healthy.getId())).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM timeout_failure WHERE work_order_id = ?", String.class, failing.getId()))
                .isEqualTo("PENDING");

        jdbc.execute("DROP TRIGGER reject_timeout_track");
        TimeoutJobExecution recovery = timeoutInspectionService.run("TEST_RECOVERY");
        assertThat(recovery.getSuccessCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM timeout_failure WHERE work_order_id = ?", String.class, failing.getId()))
                .isEqualTo("RESOLVED");
    }

    @Test
    void pendingTimeoutFailureSupportsManualCompensation() {
        WorkOrder order = createOrder(List.of(1L));
        expire(order.getId());
        jdbc.execute("""
                CREATE TRIGGER reject_timeout_track BEFORE INSERT ON work_order_track
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected timeout track failure'
                """);
        timeoutInspectionService.run("TEST");
        TimeoutFailure failure = timeoutFailureMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TimeoutFailure>lambdaQuery()
                        .eq(TimeoutFailure::getWorkOrderId, order.getId()));

        jdbc.execute("DROP TRIGGER reject_timeout_track");
        timeoutInspectionService.compensate(failure.getId(), 1L);

        assertThat(timeoutFailureMapper.selectById(failure.getId()).getStatus()).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("SELECT timeout_flag FROM work_order WHERE id = ?", Boolean.class,
                order.getId())).isTrue();
    }

    @Test
    void permissionCacheHitsInvalidatesAndRoleChangeExpiresTheSession() {
        PermissionCacheService.CacheStats before = permissionCacheService.snapshot();

        assertThat(permissionCacheService.permissions(4L)).contains("device:read", "work-order:write");
        assertThat(permissionCacheService.permissions(4L)).contains("device:read", "work-order:write");
        PermissionCacheService.CacheStats warmed = permissionCacheService.snapshot();
        assertThat(warmed.misses() - before.misses()).isEqualTo(1);
        assertThat(warmed.hits() - before.hits()).isEqualTo(1);

        String token = StpUtil.getStpLogic().createLoginSession(4L);
        assertThat(StpUtil.getStpLogic().getLoginIdByToken(token)).hasToString("4");
        rbacService.replaceRolePermissions(2L, List.of(5L), 1L);

        assertThat(StpUtil.getStpLogic().getLoginIdByToken(token)).isNull();
        assertThat(permissionCacheService.permissions(4L)).containsExactly("device:read");
        assertThat(permissionCacheService.snapshot().invalidations() - warmed.invalidations()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_audit_log WHERE action = 'ROLE_PERMISSIONS_REPLACE'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void projectDataScopeFiltersDevicePagesAndRejectsOutOfScopeWrites() {
        jdbc.update("""
                INSERT INTO project(id, project_code, project_name, status)
                VALUES (2, 'REMOTE-SITE', 'Remote Site', 'ENABLED')
                """);
        jdbc.update("""
                INSERT INTO device(sn, device_name, project_id, product_id, online_status)
                VALUES ('SCOPE-SN-001', 'Out of scope device', 2, 1, 'UNKNOWN')
                """);

        var scope = dataScopeService.forUser(4L);
        assertThat(scope.allProjects()).isFalse();
        assertThat(scope.projectIds()).containsExactly(1L);
        assertThat(deviceService.page(1, 20, null, null, scope).records())
                .extracting(Device::getProjectId).containsOnly(1L);
        assertThat(deviceService.page(1, 20, null, 2L, scope).records()).isEmpty();
        assertThatThrownBy(() -> dataScopeService.requireProject(4L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside");
    }

    @Test
    @EnabledIfSystemProperty(named = "iot.benchmark", matches = "true")
    void benchmarkImportLookupRoundTripsAgainstRowByRowCandidate() {
        int rowCount = 1_000;
        List<String> sns = java.util.stream.IntStream.range(0, rowCount)
                .mapToObj(index -> "BENCH-SN-%04d".formatted(index))
                .toList();
        for (int index = 0; index < 20; index++) {
            jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE sn = ?", Integer.class, sns.get(index));
        }

        long naiveStart = System.nanoTime();
        int naiveExisting = 0;
        for (String sn : sns) {
            jdbc.queryForObject("SELECT id FROM project WHERE project_code = 'SMART-CAMPUS'", Long.class);
            jdbc.queryForObject("SELECT id FROM product WHERE product_code = 'ENV-SENSOR'", Long.class);
            naiveExisting += jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE sn = ?", Integer.class, sn);
        }
        long naiveMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - naiveStart);

        long batchStart = System.nanoTime();
        jdbc.queryForObject("SELECT id FROM project WHERE project_code = 'SMART-CAMPUS'", Long.class);
        jdbc.queryForObject("SELECT id FROM product WHERE product_code = 'ENV-SENSOR'", Long.class);
        int batchExisting = 0;
        for (int start = 0; start < sns.size(); start += 500) {
            List<String> batch = sns.subList(start, Math.min(start + 500, sns.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            batchExisting += jdbc.queryForObject(
                    "SELECT COUNT(*) FROM device WHERE sn IN (" + placeholders + ")",
                    Integer.class, batch.toArray());
        }
        long batchMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStart);

        System.out.printf("IMPORT_LOOKUP_BENCHMARK rows=%d naiveQueries=%d naiveMs=%d "
                        + "batchQueries=%d batchMs=%d%n",
                rowCount, rowCount * 3, naiveMillis, 4, batchMillis);
        assertThat(batchExisting).isEqualTo(naiveExisting);
        assertThat(batchMillis).isLessThan(naiveMillis);
    }

    @Test
    @EnabledIfSystemProperty(named = "iot.benchmark", matches = "true")
    void benchmarkFiveHundredDeviceXmlMultiValueInsert() {
        int rowsPerRound = 500;
        int warmupRounds = 2;
        int measuredRounds = 10;
        String runPrefix = "BINS-" + Long.toUnsignedString(System.nanoTime(), 36).toUpperCase(Locale.ROOT) + "-";

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id = 1", Integer.class)).isEqualTo(1);

        List<InsertBenchmarkSample> samples = new ArrayList<>();
        try {
            for (int round = 1; round <= warmupRounds + measuredRounds; round++) {
                boolean warmup = round <= warmupRounds;
                int phaseRound = warmup ? round : round - warmupRounds;
                String roundPrefix = runPrefix + (warmup ? "W" : "M") + "%02d-".formatted(phaseRound);
                List<Device> devices = benchmarkDevices(roundPrefix, rowsPerRound);

                InsertBenchmarkSample sample = committedBatchInsert(devices);
                int inserted = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM device WHERE sn LIKE ?", Integer.class, roundPrefix + "%");
                assertThat(inserted).isEqualTo(rowsPerRound);
                assertThat(sample.affectedRows()).isEqualTo(rowsPerRound);
                assertThat(sample.mapperCalls()).isEqualTo(1);
                assertThat(sample.sqlStatements()).isEqualTo(1);
                assertThat(sample.valueTuples()).isEqualTo(rowsPerRound);
                assertThat(sample.sql()).startsWith("INSERT INTO device").contains("VALUES");
                assertThat(sample.transactionTotalNanos()).isGreaterThanOrEqualTo(sample.insertCallNanos());

                System.out.printf(Locale.ROOT,
                        "DEVICE_BATCH_INSERT_ROUND phase=%s round=%d rows=%d mapperCalls=%d "
                                + "sqlStatements=%d valueTuples=%d insertCallMs=%.3f transactionTotalMs=%.3f%n",
                        warmup ? "warmup" : "measured", phaseRound, rowsPerRound,
                        sample.mapperCalls(), sample.sqlStatements(), sample.valueTuples(),
                        nanosToMillis(sample.insertCallNanos()), nanosToMillis(sample.transactionTotalNanos()));
                if (!warmup) {
                    samples.add(sample);
                }
            }

            assertThat(samples).hasSize(measuredRounds);
            printInsertSummary("insertCallMs", samples.stream().map(InsertBenchmarkSample::insertCallNanos).toList());
            printInsertSummary("transactionTotalMs",
                    samples.stream().map(InsertBenchmarkSample::transactionTotalNanos).toList());
            verifyUniqueConstraintRollsBackTheWholeWrite(runPrefix + "TX-");
        } finally {
            jdbc.update("DELETE FROM device WHERE sn LIKE ?", runPrefix + "%");
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM device WHERE sn LIKE ?", Integer.class, runPrefix + "%")).isZero();
    }

    private InsertBenchmarkSample committedBatchInsert(List<Device> devices) {
        batchInsertProbe.reset();
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName("device-batch-insert-benchmark");
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        long transactionStart = System.nanoTime();
        TransactionStatus transaction = transactionManager.getTransaction(definition);
        int affectedRows;
        long insertCallNanos;
        try {
            long insertStart = System.nanoTime();
            affectedRows = deviceMapper.batchInsert(devices);
            insertCallNanos = System.nanoTime() - insertStart;
            transactionManager.commit(transaction);
        } catch (RuntimeException exception) {
            if (!transaction.isCompleted()) {
                transactionManager.rollback(transaction);
            }
            throw exception;
        }
        long transactionTotalNanos = System.nanoTime() - transactionStart;
        BatchInsertStatementProbe.Snapshot probe = batchInsertProbe.snapshot();
        return new InsertBenchmarkSample(insertCallNanos, transactionTotalNanos, affectedRows,
                probe.mapperCalls(), probe.sqlStatements(), probe.valueTuples(), probe.sql());
    }

    private void verifyUniqueConstraintRollsBackTheWholeWrite(String prefix) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus transaction = transactionManager.getTransaction(definition);
        boolean duplicateRejected = false;
        try {
            deviceMapper.batchInsert(List.of(device(prefix + "FIRST")));
            deviceMapper.batchInsert(List.of(device(prefix + "DUP"), device(prefix + "DUP")));
            transactionManager.commit(transaction);
        } catch (RuntimeException expected) {
            duplicateRejected = true;
            if (!transaction.isCompleted()) {
                transactionManager.rollback(transaction);
            }
        }
        assertThat(duplicateRejected).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM device WHERE sn LIKE ?", Integer.class, prefix + "%")).isZero();
    }

    private List<Device> benchmarkDevices(String prefix, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> device(prefix + "%03d".formatted(index)))
                .toList();
    }

    private void printInsertSummary(String metric, List<Long> rawNanos) {
        List<Long> sorted = rawNanos.stream().sorted(Comparator.naturalOrder()).toList();
        double minimum = nanosToMillis(sorted.getFirst());
        double median = sorted.size() % 2 == 0
                ? (nanosToMillis(sorted.get(sorted.size() / 2 - 1)) + nanosToMillis(sorted.get(sorted.size() / 2))) / 2
                : nanosToMillis(sorted.get(sorted.size() / 2));
        double average = rawNanos.stream().mapToDouble(this::nanosToMillis).average().orElseThrow();
        int p95Index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        double p95 = nanosToMillis(sorted.get(p95Index));
        System.out.printf(Locale.ROOT,
                "DEVICE_BATCH_INSERT_SUMMARY metric=%s rounds=%d min=%.3f median=%.3f average=%.3f p95=%.3f%n",
                metric, rawNanos.size(), minimum, median, average, p95);
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record InsertBenchmarkSample(long insertCallNanos, long transactionTotalNanos, int affectedRows,
                                         int mapperCalls, int sqlStatements, int valueTuples, String sql) {
    }

    private Callable<Boolean> acceptAttempt(Long orderId, Long userId, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            try {
                workOrderService.accept(orderId, userId);
                return true;
            } catch (BusinessException expected) {
                return false;
            }
        };
    }

    private WorkOrder createOrder(List<Long> deviceIds) {
        return workOrderService.create(new WorkOrderService.CreateCommand(
                1L, deviceIds, "Field incident", "Investigate connectivity", "NORMAL"), 1L);
    }

    private void expire(Long workOrderId) {
        jdbc.update("UPDATE work_order SET deadline_time = DATE_SUB(NOW(3), INTERVAL 1 HOUR) WHERE id = ?",
                workOrderId);
    }

    private String alarmJson(String eventId, String deviceSn, String eventType) {
        return """
                {"eventId":"%s","deviceSn":"%s","eventType":"%s","severity":"HIGH",
                 "occurredAt":"2026-09-15T10:00:00","data":{"source":"integration-test"}}
                """.formatted(eventId, deviceSn, eventType);
    }

    private DeviceImportRow row(String sn, String name, String projectCode, String productCode, String imei) {
        DeviceImportRow row = new DeviceImportRow();
        row.setSn(sn);
        row.setDeviceName(name);
        row.setProjectCode(projectCode);
        row.setProductCode(productCode);
        row.setImei(imei);
        return row;
    }

    private byte[] workbook(List<DeviceImportRow> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output, DeviceImportRow.class).autoCloseStream(false).sheet("Devices").doWrite(rows);
        return output.toByteArray();
    }

    private Device device(String sn) {
        Device device = new Device();
        device.setSn(sn);
        device.setDeviceName(sn);
        device.setProjectId(1L);
        device.setProductId(1L);
        device.setOnlineStatus("UNKNOWN");
        return device;
    }
}
