package io.github.xianspiderman.iotops.auth;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RbacMapper {
    @Select("""
            SELECT DISTINCT p.permission_code
              FROM sys_permission p
              JOIN sys_role_permission rp ON rp.permission_id = p.id
              JOIN sys_role r ON r.id = rp.role_id AND r.status = 'ENABLED'
              JOIN sys_user_role ur ON ur.role_id = r.id
             WHERE ur.user_id = #{userId}
             ORDER BY p.permission_code
            """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRole(@Param("roleId") Long roleId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId} ORDER BY role_id")
    List<Long> selectRoleIdsByUser(@Param("userId") Long userId);

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId} ORDER BY permission_id")
    List<Long> selectPermissionIdsByRole(@Param("roleId") Long roleId);

    @Select("SELECT project_id FROM sys_user_data_scope WHERE user_id = #{userId} AND scope_type = 'PROJECT'")
    List<Long> selectProjectIdsByUser(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_user_data_scope WHERE user_id = #{userId} AND scope_type = 'ALL'")
    int countAllScope(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_permission(role_id, permission_id) VALUES(#{roleId}, #{permissionId})")
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM sys_user_data_scope WHERE user_id = #{userId}")
    int deleteUserScopes(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_data_scope(user_id, scope_type, project_id) VALUES(#{userId}, #{scopeType}, #{projectId})")
    int insertUserScope(@Param("userId") Long userId, @Param("scopeType") String scopeType,
                        @Param("projectId") Long projectId);
}
