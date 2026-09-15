package io.github.xianspiderman.iotops.timeout;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.common.PageResult;
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

    @PostMapping("/run")
    public ApiResponse<TimeoutJobExecution> run() {
        return ApiResponse.ok(service.run("MANUAL"));
    }

    @GetMapping("/executions")
    public ApiResponse<PageResult<TimeoutJobExecution>> executions(@RequestParam(defaultValue = "1") long page,
                                                                   @RequestParam(defaultValue = "20") long size) {
        IPage<TimeoutJobExecution> result = executionMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<TimeoutJobExecution>lambdaQuery().orderByDesc(TimeoutJobExecution::getId));
        return ApiResponse.ok(new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords()));
    }

    @GetMapping("/failures")
    public ApiResponse<PageResult<TimeoutFailure>> failures(@RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "20") long size,
                                                             @RequestParam(required = false) String status) {
        IPage<TimeoutFailure> result = failureMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<TimeoutFailure>lambdaQuery()
                        .eq(status != null && !status.isBlank(), TimeoutFailure::getStatus, status)
                        .orderByDesc(TimeoutFailure::getUpdatedAt));
        return ApiResponse.ok(new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords()));
    }

    @PostMapping("/failures/{id}/compensate")
    public ApiResponse<Void> compensate(@PathVariable Long id) {
        service.compensate(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }
}
