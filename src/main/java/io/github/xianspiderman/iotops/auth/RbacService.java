package io.github.xianspiderman.iotops.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.audit.AuditService;
import io.github.xianspiderman.iotops.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RbacService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final RbacMapper rbacMapper;
    private final PermissionCacheService cacheService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public List<UserAccessView> users() {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().orderByAsc(SysUser::getId)).stream()
                .map(user -> new UserAccessView(user, rbacMapper.selectRoleIdsByUser(user.getId()),
                        rbacMapper.countAllScope(user.getId()) > 0,
                        rbacMapper.selectProjectIdsByUser(user.getId())))
                .toList();
    }

    public List<RoleView> roles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId)).stream()
                .map(role -> new RoleView(role, rbacMapper.selectPermissionIdsByRole(role.getId())))
                .toList();
    }

    public List<SysPermission> permissions() {
        return permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .orderByAsc(SysPermission::getPermissionCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(CreateUserCommand command, Long operatorId) {
        if (userMapper.exists(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, command.username()))) {
            throw new BusinessException("USERNAME_EXISTS", "Username already exists");
        }
        SysUser user = new SysUser();
        user.setUsername(command.username().trim());
        user.setDisplayName(command.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setStatus("ENABLED");
        userMapper.insert(user);
        auditService.record(operatorId, "USER_CREATE", "USER", String.valueOf(user.getId()),
                Map.of("username", user.getUsername()));
        return userMapper.selectById(user.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public SysRole createRole(String code, String name, Long operatorId) {
        String normalizedCode = code.trim().toUpperCase();
        if (roleMapper.exists(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, normalizedCode))) {
            throw new BusinessException("ROLE_CODE_EXISTS", "Role code already exists");
        }
        SysRole role = new SysRole();
        role.setRoleCode(normalizedCode);
        role.setRoleName(name.trim());
        role.setStatus("ENABLED");
        roleMapper.insert(role);
        auditService.record(operatorId, "ROLE_CREATE", "ROLE", String.valueOf(role.getId()),
                Map.of("roleCode", role.getRoleCode()));
        return roleMapper.selectById(role.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceUserRoles(Long userId, List<Long> roleIds, Long operatorId) {
        requireUser(userId);
        Set<Long> distinct = new LinkedHashSet<>(roleIds);
        requireExistingRoles(distinct);
        cacheService.evictRequired(userId);
        rbacMapper.deleteUserRoles(userId);
        distinct.forEach(roleId -> rbacMapper.insertUserRole(userId, roleId));
        auditService.record(operatorId, "USER_ROLES_REPLACE", "USER", String.valueOf(userId),
                Map.of("roleIds", distinct));
        afterCommitInvalidateAndLogout(List.of(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceRolePermissions(Long roleId, List<Long> permissionIds, Long operatorId) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "Role does not exist");
        }
        Set<Long> distinct = new LinkedHashSet<>(permissionIds);
        if (!distinct.isEmpty() && permissionMapper.selectBatchIds(distinct).size() != distinct.size()) {
            throw new BusinessException("PERMISSION_NOT_FOUND", "One or more permissions do not exist");
        }
        List<Long> affectedUsers = rbacMapper.selectUserIdsByRole(roleId);
        affectedUsers.forEach(cacheService::evictRequired);
        rbacMapper.deleteRolePermissions(roleId);
        distinct.forEach(permissionId -> rbacMapper.insertRolePermission(roleId, permissionId));
        auditService.record(operatorId, "ROLE_PERMISSIONS_REPLACE", "ROLE", String.valueOf(roleId),
                Map.of("permissionIds", distinct));
        afterCommitInvalidateAndLogout(affectedUsers);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceDataScope(Long userId, boolean allProjects, List<Long> projectIds, Long operatorId) {
        requireUser(userId);
        Set<Long> distinct = new LinkedHashSet<>(projectIds);
        rbacMapper.deleteUserScopes(userId);
        if (allProjects) {
            rbacMapper.insertUserScope(userId, "ALL", null);
        } else {
            distinct.forEach(projectId -> rbacMapper.insertUserScope(userId, "PROJECT", projectId));
        }
        auditService.record(operatorId, "USER_SCOPE_REPLACE", "USER", String.valueOf(userId),
                Map.of("allProjects", allProjects, "projectIds", distinct));
        afterCommitLogout(List.of(userId));
    }

    private void requireUser(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException("USER_NOT_FOUND", "User does not exist");
        }
    }

    private void requireExistingRoles(Set<Long> roleIds) {
        if (!roleIds.isEmpty() && roleMapper.selectBatchIds(roleIds).size() != roleIds.size()) {
            throw new BusinessException("ROLE_NOT_FOUND", "One or more roles do not exist");
        }
    }

    private void afterCommitInvalidateAndLogout(List<Long> userIds) {
        registerAfterCommit(() -> userIds.forEach(userId -> {
            cacheService.evictBestEffort(userId);
            StpUtil.logout(userId);
        }));
    }

    private void afterCommitLogout(List<Long> userIds) {
        registerAfterCommit(() -> userIds.forEach(StpUtil::logout));
    }

    private void registerAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public record UserAccessView(SysUser user, List<Long> roleIds, boolean allProjects, List<Long> projectIds) { }
    public record RoleView(SysRole role, List<Long> permissionIds) { }
    public record CreateUserCommand(String username, String displayName, String password) { }
}
