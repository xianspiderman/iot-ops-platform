package io.github.xianspiderman.iotops.auth;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
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
@RequestMapping("/rbac")
@RequiredArgsConstructor
@SaCheckPermission("rbac:manage")
public class RbacController {
    private final RbacService service;
    private final PermissionCacheService cacheService;

    @GetMapping("/users")
    public ApiResponse<List<RbacService.UserAccessView>> users() {
        return ApiResponse.ok(service.users());
    }

    @PostMapping("/users")
    public ApiResponse<SysUser> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(service.createUser(new RbacService.CreateUserCommand(
                request.username(), request.displayName(), request.password()), StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RbacService.RoleView>> roles() {
        return ApiResponse.ok(service.roles());
    }

    @PostMapping("/roles")
    public ApiResponse<SysRole> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(service.createRole(request.roleCode(), request.roleName(), StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<SysPermission>> permissions() {
        return ApiResponse.ok(service.permissions());
    }

    @PutMapping("/users/{id}/roles")
    public ApiResponse<Void> userRoles(@PathVariable Long id, @RequestBody IdsRequest request) {
        service.replaceUserRoles(id, request.ids(), StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<Void> rolePermissions(@PathVariable Long id, @RequestBody IdsRequest request) {
        service.replaceRolePermissions(id, request.ids(), StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    @PutMapping("/users/{id}/data-scope")
    public ApiResponse<Void> dataScope(@PathVariable Long id, @RequestBody DataScopeRequest request) {
        service.replaceDataScope(id, request.allProjects(), request.projectIds(), StpUtil.getLoginIdAsLong());
        return ApiResponse.ok();
    }

    @GetMapping("/cache-stats")
    public ApiResponse<PermissionCacheService.CacheStats> cacheStats() {
        return ApiResponse.ok(cacheService.snapshot());
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9._-]{2,63}") String username,
            @NotBlank @Size(max = 64) String displayName,
            @NotBlank @Size(min = 10, max = 72) String password) { }
    public record CreateRoleRequest(@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{2,63}") String roleCode,
                                    @NotBlank @Size(max = 128) String roleName) { }
    public record IdsRequest(@NotNull List<@NotNull Long> ids) { }
    public record DataScopeRequest(boolean allProjects, @NotNull List<@NotNull Long> projectIds) { }
}
