CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(96) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(24) NOT NULL DEFAULT 'API',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_data_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    project_id BIGINT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_data_scope_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_data_scope_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT uk_data_scope_user_project UNIQUE (user_id, project_id),
    INDEX idx_data_scope_user_type (user_id, scope_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE business_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id BIGINT,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(48) NOT NULL,
    target_id VARCHAR(96),
    detail JSON,
    request_id VARCHAR(64),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
    INDEX idx_audit_target_time (target_type, target_id, created_at),
    INDEX idx_audit_operator_time (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_user(id, username, password_hash, display_name, status)
VALUES (4, 'operator', '{BOOTSTRAP_OPERATOR}', 'Operations Demo', 'ENABLED');

INSERT INTO sys_role(id, role_code, role_name) VALUES
    (1, 'PLATFORM_ADMIN', 'Platform administrator'),
    (2, 'PROJECT_OPERATOR', 'Project operator');

INSERT INTO sys_permission(id, permission_code, permission_name) VALUES
    (1, 'project:read', 'Read projects'),
    (2, 'project:write', 'Manage projects'),
    (3, 'product:read', 'Read products'),
    (4, 'product:write', 'Manage products'),
    (5, 'device:read', 'Read devices'),
    (6, 'device:write', 'Manage devices'),
    (7, 'device:import', 'Import devices'),
    (8, 'work-order:read', 'Read work orders'),
    (9, 'work-order:write', 'Operate work orders'),
    (10, 'alarm:read', 'Read alarms'),
    (11, 'alarm:recover', 'Recover alarm messages'),
    (12, 'timeout:read', 'Read timeout inspections'),
    (13, 'timeout:execute', 'Run and recover timeout inspections'),
    (14, 'rbac:manage', 'Manage users, roles and permissions'),
    (15, 'audit:read', 'Read business audit records'),
    (16, 'device-taxonomy:write', 'Manage device groups and tags');

INSERT INTO sys_user_role(user_id, role_id) VALUES (1, 1), (4, 2);
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT 1, id FROM sys_permission;
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT 2, id FROM sys_permission
WHERE permission_code IN ('project:read', 'product:read', 'device:read', 'device:write', 'device:import',
                          'work-order:read', 'work-order:write', 'alarm:read', 'timeout:read');
INSERT INTO sys_user_data_scope(user_id, scope_type, project_id) VALUES
    (1, 'ALL', NULL),
    (4, 'PROJECT', 1);
