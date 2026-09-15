package io.github.xianspiderman.iotops.alarm;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AlarmController {
    private final AlarmMapper alarmMapper;
    private final AlarmEventRecordMapper recordMapper;
    private final AlarmEventProcessor processor;
    private final DataScopeService dataScopeService;

    @GetMapping("/alarms")
    @SaCheckPermission("alarm:read")
    public ApiResponse<PageResult<Alarm>> alarms(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        DataScope scope = dataScopeService.forUser(StpUtil.getLoginIdAsLong());
        if (!scope.allProjects() && scope.projectIds().isEmpty()) {
            return ApiResponse.ok(new PageResult<>(page, Math.min(size, 100), 0, java.util.List.of()));
        }
        String visibleProjects = scope.projectIds().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        IPage<Alarm> result = alarmMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<Alarm>lambdaQuery()
                        .inSql(!scope.allProjects(), Alarm::getDeviceId,
                                "SELECT id FROM device WHERE project_id IN (" + visibleProjects + ")")
                        .orderByDesc(Alarm::getId));
        return ApiResponse.ok(new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords()));
    }

    @GetMapping("/alarm-events/{eventId}")
    @SaCheckPermission("alarm:recover")
    public ApiResponse<AlarmEventRecord> event(@PathVariable String eventId) {
        requireAllProjectRecoveryScope();
        AlarmEventRecord record = recordMapper.selectOne(Wrappers.<AlarmEventRecord>lambdaQuery()
                .eq(AlarmEventRecord::getEventId, eventId));
        if (record == null) {
            throw new BusinessException("ALARM_EVENT_NOT_FOUND", "Alarm event record does not exist");
        }
        return ApiResponse.ok(record);
    }

    @GetMapping("/alarm-events/failures")
    @SaCheckPermission("alarm:recover")
    public ApiResponse<PageResult<AlarmEventRecord>> failures(@RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "20") long size) {
        requireAllProjectRecoveryScope();
        IPage<AlarmEventRecord> result = recordMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<AlarmEventRecord>lambdaQuery().ne(AlarmEventRecord::getProcessStatus, "PROCESSED")
                        .orderByDesc(AlarmEventRecord::getUpdatedAt));
        return ApiResponse.ok(new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords()));
    }

    @PostMapping("/alarm-events/failures/{id}/confirm")
    @SaCheckPermission("alarm:recover")
    public ApiResponse<Void> confirm(@PathVariable Long id, @Valid @RequestBody CorrectionRequest request) {
        requireAllProjectRecoveryScope();
        processor.confirm(id, request.correctedPayload().toString(), StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    @PostMapping("/alarm-events/failures/{id}/reprocess")
    @SaCheckPermission("alarm:recover")
    public ApiResponse<AlarmProcessOutcome> reprocess(@PathVariable Long id) {
        requireAllProjectRecoveryScope();
        return ApiResponse.ok(processor.reprocess(id));
    }

    private void requireAllProjectRecoveryScope() {
        if (!dataScopeService.forUser(StpUtil.getLoginIdAsLong()).allProjects()) {
            throw new BusinessException("DATA_SCOPE_DENIED", "Alarm recovery requires all-project scope");
        }
    }

    public record CorrectionRequest(@NotNull JsonNode correctedPayload) {
    }
}
