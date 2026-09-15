package io.github.xianspiderman.iotops.integration;

import com.alibaba.excel.EasyExcel;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import io.github.xianspiderman.iotops.device.Device;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
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
        "iot-ops.import.insert-batch-size=2"
})
class MySqlBusinessFlowIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("iot_ops_test")
            .withUsername("iot_ops")
            .withPassword("iot_ops_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
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

    @BeforeEach
    void cleanBusinessRows() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_submit_track");
        jdbc.update("DELETE FROM work_order_track");
        jdbc.update("DELETE FROM work_order_device");
        jdbc.update("DELETE FROM work_order");
        jdbc.update("DELETE FROM device_import_error");
        jdbc.update("DELETE FROM device_import_task");
        jdbc.update("DELETE FROM device WHERE id > 2");
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
