package io.github.xianspiderman.iotops.timeout;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import io.github.xianspiderman.iotops.workorder.WorkOrder;
import io.github.xianspiderman.iotops.workorder.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/timeout-inspections")
@RequiredArgsConstructor
public class TimeoutController {
    private final TimeoutInspectionService service;
    private final TimeoutJobExecutionMapper executionMapper;
    private final TimeoutFailureMapper failureMapper;
    private final DataScopeService dataScopeService;
    private final WorkOrderMapper workOrderMapper;

    @PostMapping("/run")
    @SaCheckPermission("timeout:execute")
    public ApiResponse<TimeoutJobExecution> run() {
        if (!dataScopeService.forUser(StpUtil.getLoginIdAsLong()).allProjects()) {
            throw new BusinessException("DATA_SCOPE_DENIED", "Full timeout inspection requires all-project scope");
        }
        return ApiResponse.ok(service.run("MANUAL"));
    }

    @GetMapping("/executions")
    @SaCheckPermission("timeout:read")
    public ApiResponse<PageResult<TimeoutJobExecution>> executions(@RequestParam(defaultValue = "1") long page,
                                                                   @RequestParam(defaultValue = "20") long size) {
        DataScope scope = dataScopeService.forUser(StpUtil.getLoginIdAsLong());
        long boundedSize = Math.min(size, 100);
        if (!scope.allProjects()) {
            return ApiResponse.ok(new PageResult<>(page, boundedSize, 0, java.util.List.of()));
        }
        IPage<TimeoutJobExecution> result = executionMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<TimeoutJobExecution>lambdaQuery().orderByDesc(TimeoutJobExecution::getId));
        return ApiResponse.ok(new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords()));
    }

    @GetMapping("/failures")
    @SaCheckPermission("timeout:read")
    public ApiResponse<PageResult<TimeoutFailure>> failures(@RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "20") long size,
                                                             @RequestParam(required = false) String status) {
        DataScope scope = dataScopeService.forUser(StpUtil.getLoginIdAsLong());
        if (!scope.allProjects() && scope.projectIds().isEmpty()) {
            return ApiResponse.ok(new PageResult<>(page, Math.min(size, 100), 0, java.util.List.of()));
        }
        String projectIds = scope.projectIds().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        IPage<TimeoutFailure> result = failureMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<TimeoutFailure>lambdaQuery()
                        .eq(status != null && !status.isBlank(), TimeoutFailure::getStatus, status)
                        .inSql(!scope.allProjects(), TimeoutFailure::getWorkOrderId,
                                "SELECT id FROM work_order WHERE project_id IN (" + projectIds + ")")
                        .orderByDesc(TimeoutFailure::getUpdatedAt));
        return ApiResponse.ok(new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords()));
    }

    @PostMapping("/failures/{id}/compensate")
    @SaCheckPermission("timeout:execute")
    public ApiResponse<Void> compensate(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        TimeoutFailure failure = failureMapper.selectById(id);
        if (failure == null) {
            throw new BusinessException("TIMEOUT_FAILURE_NOT_FOUND", "Timeout failure does not exist");
        }
        WorkOrder order = workOrderMapper.selectById(failure.getWorkOrderId());
        if (order == null || !dataScopeService.forUser(userId).permits(order.getProjectId())) {
            throw new BusinessException("TIMEOUT_FAILURE_NOT_FOUND", "Timeout failure does not exist in your scope");
        }
        service.compensate(id, userId);
        return ApiResponse.ok();
    }
}
