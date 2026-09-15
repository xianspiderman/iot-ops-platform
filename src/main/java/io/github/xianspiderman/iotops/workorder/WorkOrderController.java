package io.github.xianspiderman.iotops.workorder;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderService service;
    private final DataScopeService dataScopeService;

    @GetMapping
    @SaCheckPermission("work-order:read")
    public ApiResponse<PageResult<WorkOrderView>> page(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "20") long size,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.page(page, size, status,
                dataScopeService.forUser(StpUtil.getLoginIdAsLong())));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("work-order:read")
    public ApiResponse<WorkOrderDetail> detail(@PathVariable Long id) {
        WorkOrderDetail detail = service.detail(id);
        dataScopeService.requireProject(StpUtil.getLoginIdAsLong(), detail.workOrder().getProjectId());
        return ApiResponse.ok(detail);
    }

    @PostMapping
    @SaCheckPermission("work-order:write")
    public ApiResponse<WorkOrder> create(@Valid @RequestBody CreateRequest request) {
        dataScopeService.requireProject(StpUtil.getLoginIdAsLong(), request.projectId());
        return ApiResponse.ok(service.create(new WorkOrderService.CreateCommand(
                request.projectId(), request.deviceIds(), request.title(), request.description(), request.priority()),
                StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/{id}/accept")
    @SaCheckPermission("work-order:write")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        requireScope(id);
        service.accept(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    @SaCheckPermission("work-order:write")
    public ApiResponse<Void> submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest request) {
        requireScope(id);
        service.submit(id, StpUtil.getLoginIdAsLong(), request.solution());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/verify")
    @SaCheckPermission("work-order:write")
    public ApiResponse<Void> verify(@PathVariable Long id, @Valid @RequestBody VerifyRequest request) {
        requireScope(id);
        service.verifyAndClose(id, StpUtil.getLoginIdAsLong(), request.remark());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("work-order:write")
    public ApiResponse<Void> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        requireScope(id);
        service.cancel(id, StpUtil.getLoginIdAsLong(), request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/transfer")
    @SaCheckPermission("work-order:write")
    public ApiResponse<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        requireScope(id);
        service.transfer(id, StpUtil.getLoginIdAsLong(), request.newHandlerId(), request.reason());
        return ApiResponse.ok();
    }

    private void requireScope(Long workOrderId) {
        WorkOrderDetail detail = service.detail(workOrderId);
        dataScopeService.requireProject(StpUtil.getLoginIdAsLong(), detail.workOrder().getProjectId());
    }

    public record CreateRequest(@NotNull Long projectId,
                                @NotEmpty List<@NotNull Long> deviceIds,
                                @NotBlank @Size(max = 160) String title,
                                @NotBlank @Size(max = 1000) String description,
                                @NotBlank String priority) {
    }

    public record SubmitRequest(@NotBlank @Size(max = 1000) String solution) {
    }

    public record VerifyRequest(@Size(max = 1000) String remark) {
    }

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record TransferRequest(@NotNull Long newHandlerId,
                                  @NotBlank @Size(max = 800) String reason) {
    }
}
