package io.github.xianspiderman.iotops.workorder;

import cn.dev33.satoken.stp.StpUtil;
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

    @GetMapping
    public ApiResponse<PageResult<WorkOrderView>> page(@RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "20") long size,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.page(page, size, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkOrderDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    public ApiResponse<WorkOrder> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(new WorkOrderService.CreateCommand(
                request.projectId(), request.deviceIds(), request.title(), request.description(), request.priority()),
                StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        service.accept(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest request) {
        service.submit(id, StpUtil.getLoginIdAsLong(), request.solution());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<Void> verify(@PathVariable Long id, @Valid @RequestBody VerifyRequest request) {
        service.verifyAndClose(id, StpUtil.getLoginIdAsLong(), request.remark());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        service.cancel(id, StpUtil.getLoginIdAsLong(), request.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        service.transfer(id, StpUtil.getLoginIdAsLong(), request.newHandlerId(), request.reason());
        return ApiResponse.ok();
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
