UPDATE sys_user SET display_name = '平台管理员' WHERE username = 'admin';
UPDATE sys_user SET display_name = '项目运维员' WHERE username = 'operator';

UPDATE project
SET project_name = '智慧园区交付项目', description = '园区环境传感器公开演示项目'
WHERE project_code = 'SMART-CAMPUS';

UPDATE product
SET product_name = '环境传感器'
WHERE product_code = 'ENV-SENSOR';

UPDATE device SET device_name = '北楼环境传感器' WHERE sn = 'DEMO-SN-001';
UPDATE device SET device_name = '南楼环境传感器' WHERE sn = 'DEMO-SN-002';

UPDATE sys_role SET role_name = '平台管理员' WHERE role_code = 'PLATFORM_ADMIN';
UPDATE sys_role SET role_name = '项目运维员' WHERE role_code = 'PROJECT_OPERATOR';

UPDATE sys_permission SET permission_name = CASE permission_code
    WHEN 'project:read' THEN '查看项目'
    WHEN 'project:write' THEN '管理项目'
    WHEN 'product:read' THEN '查看产品'
    WHEN 'product:write' THEN '管理产品'
    WHEN 'device:read' THEN '查看设备'
    WHEN 'device:write' THEN '管理设备'
    WHEN 'device:import' THEN '导入设备'
    WHEN 'work-order:read' THEN '查看工单'
    WHEN 'work-order:write' THEN '处理工单'
    WHEN 'alarm:read' THEN '查看告警'
    WHEN 'alarm:recover' THEN '恢复告警'
    WHEN 'timeout:read' THEN '查看巡检'
    WHEN 'timeout:execute' THEN '执行巡检与补偿'
    WHEN 'rbac:manage' THEN '管理权限'
    WHEN 'audit:read' THEN '查看审计'
    WHEN 'device-taxonomy:write' THEN '管理设备分组与标签'
    ELSE permission_name
END;
