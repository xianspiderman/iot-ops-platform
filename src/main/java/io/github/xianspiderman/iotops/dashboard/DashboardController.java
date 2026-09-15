package io.github.xianspiderman.iotops.dashboard;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.alarm.Alarm;
import io.github.xianspiderman.iotops.alarm.AlarmEventRecord;
import io.github.xianspiderman.iotops.alarm.AlarmEventRecordMapper;
import io.github.xianspiderman.iotops.alarm.AlarmMapper;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import io.github.xianspiderman.iotops.timeout.TimeoutFailure;
import io.github.xianspiderman.iotops.timeout.TimeoutFailureMapper;
import io.github.xianspiderman.iotops.workorder.WorkOrder;
import io.github.xianspiderman.iotops.workorder.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DataScopeService scopeService;
    private final DeviceMapper deviceMapper;
    private final WorkOrderMapper workOrderMapper;
    private final AlarmMapper alarmMapper;
    private final AlarmEventRecordMapper eventRecordMapper;
    private final TimeoutFailureMapper timeoutFailureMapper;

    @GetMapping("/summary")
    public ApiResponse<Summary> summary() {
        DataScope scope = scopeService.forUser(StpUtil.getLoginIdAsLong());
        if (!scope.allProjects() && scope.projectIds().isEmpty()) {
            return ApiResponse.ok(new Summary(0, 0, 0, 0, 0));
        }
        long devices = StpUtil.hasPermission("device:read")
                ? deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                    .in(!scope.allProjects(), Device::getProjectId, scope.projectIds())) : 0;
        long activeOrders = StpUtil.hasPermission("work-order:read")
                ? workOrderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                    .in(!scope.allProjects(), WorkOrder::getProjectId, scope.projectIds())
                    .notIn(WorkOrder::getStatus, "CLOSED", "CANCELED")) : 0;
        String projectIds = scope.projectIds().stream().map(String::valueOf).collect(Collectors.joining(","));
        long openAlarms = StpUtil.hasPermission("alarm:read")
                ? alarmMapper.selectCount(Wrappers.<Alarm>lambdaQuery()
                    .eq(Alarm::getStatus, "OPEN")
                    .inSql(!scope.allProjects(), Alarm::getDeviceId,
                            "SELECT id FROM device WHERE project_id IN (" + projectIds + ")")) : 0;
        long badMessages = StpUtil.hasPermission("alarm:recover")
                ? eventRecordMapper.selectCount(Wrappers.<AlarmEventRecord>lambdaQuery()
                    .ne(AlarmEventRecord::getProcessStatus, "PROCESSED")) : 0;
        long timeoutFailures = StpUtil.hasPermission("timeout:read")
                ? timeoutFailureMapper.selectCount(Wrappers.<TimeoutFailure>lambdaQuery()
                    .eq(TimeoutFailure::getStatus, "PENDING")
                    .inSql(!scope.allProjects(), TimeoutFailure::getWorkOrderId,
                            "SELECT id FROM work_order WHERE project_id IN (" + projectIds + ")")) : 0;
        return ApiResponse.ok(new Summary(devices, activeOrders, openAlarms, badMessages, timeoutFailures));
    }

    public record Summary(long devices, long activeWorkOrders, long openAlarms,
                          long badMessages, long timeoutFailures) { }
}
