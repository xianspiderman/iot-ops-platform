package io.github.xianspiderman.iotops.device;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService service;
    private final DataScopeService dataScopeService;

    @GetMapping
    @SaCheckPermission("device:read")
    public ApiResponse<PageResult<Device>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(service.page(page, size, keyword, projectId,
                dataScopeService.forUser(StpUtil.getLoginIdAsLong())));
    }

    @PostMapping
    @SaCheckPermission("device:write")
    public ApiResponse<Device> create(@Valid @RequestBody DeviceRequest request) {
        dataScopeService.requireProject(StpUtil.getLoginIdAsLong(), request.projectId());
        return ApiResponse.ok(service.create(new DeviceService.DeviceCommand(
                request.sn(), request.deviceName(), request.projectId(), request.productId(),
                request.imei(), request.mac(), request.firmwareVersion())));
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{2,63}") String sn,
            @NotBlank @Size(max = 128) String deviceName,
            @NotNull Long projectId,
            @NotNull Long productId,
            @Size(max = 32) String imei,
            @Size(max = 32) String mac,
            @Size(max = 64) String firmwareVersion) {
    }
}
