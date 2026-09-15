package io.github.xianspiderman.iotops.device.taxonomy;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/device-taxonomy")
@RequiredArgsConstructor
public class DeviceTaxonomyController {
    private final DeviceTaxonomyService service;
    private final DataScopeService scopeService;

    @GetMapping("/groups")
    @SaCheckPermission("device:read")
    public ApiResponse<List<DeviceTaxonomyService.GroupView>> groups() {
        return ApiResponse.ok(service.groups(scope()));
    }

    @GetMapping("/tags")
    @SaCheckPermission("device:read")
    public ApiResponse<List<DeviceTaxonomyService.TagView>> tags() {
        return ApiResponse.ok(service.tags(scope()));
    }

    @PostMapping("/groups")
    @SaCheckPermission("device-taxonomy:write")
    public ApiResponse<DeviceGroup> createGroup(@Valid @RequestBody GroupRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        scopeService.requireProject(userId, request.projectId());
        return ApiResponse.ok(service.createGroup(request.projectId(), request.groupName(), request.description(), userId));
    }

    @PostMapping("/tags")
    @SaCheckPermission("device-taxonomy:write")
    public ApiResponse<DeviceTag> createTag(@Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(service.createTag(request.tagName(), request.tagColor(), StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/groups/{id}/devices")
    @SaCheckPermission("device-taxonomy:write")
    public ApiResponse<Void> groupDevices(@PathVariable Long id, @RequestBody IdsRequest request) {
        service.replaceGroupDevices(id, request.deviceIds(), scope(), StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    @PutMapping("/tags/{id}/devices")
    @SaCheckPermission("device-taxonomy:write")
    public ApiResponse<Void> tagDevices(@PathVariable Long id, @RequestBody IdsRequest request) {
        service.replaceTagDevices(id, request.deviceIds(), scope(), StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    private DataScope scope() {
        return scopeService.forUser(StpUtil.getLoginIdAsLong());
    }

    public record GroupRequest(@NotNull Long projectId, @NotBlank @Size(max = 128) String groupName,
                               @Size(max = 500) String description) { }
    public record TagRequest(@NotBlank @Size(max = 64) String tagName,
                             @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String tagColor) { }
    public record IdsRequest(@NotNull List<@NotNull Long> deviceIds) { }
}
