CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_project_code UNIQUE (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    model VARCHAR(128),
    communication_type VARCHAR(32),
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_product_code UNIQUE (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sn VARCHAR(64) NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    project_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    imei VARCHAR(32),
    mac VARCHAR(32),
    firmware_version VARCHAR(64),
    online_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    last_communication_time DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_device_sn UNIQUE (sn),
    CONSTRAINT fk_device_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_device_product FOREIGN KEY (product_id) REFERENCES product(id),
    INDEX idx_device_project (project_id),
    INDEX idx_device_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE device_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    group_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_group_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT uk_group_project_name UNIQUE (project_id, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE device_group_device (
    group_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (group_id, device_id),
    CONSTRAINT fk_group_device_group FOREIGN KEY (group_id) REFERENCES device_group(id),
    CONSTRAINT fk_group_device_device FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE device_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag_name VARCHAR(64) NOT NULL,
    tag_color VARCHAR(16) NOT NULL DEFAULT '#409EFF',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_device_tag_name UNIQUE (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE device_tag_device (
    tag_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tag_id, device_id),
    CONSTRAINT fk_tag_device_tag FOREIGN KEY (tag_id) REFERENCES device_tag(id),
    CONSTRAINT fk_tag_device_device FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_no VARCHAR(40) NOT NULL,
    project_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITING',
    creator_id BIGINT NOT NULL,
    handler_id BIGINT,
    solution VARCHAR(1000),
    deadline_time DATETIME(3) NOT NULL,
    accept_time DATETIME(3),
    close_time DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_work_order_no UNIQUE (work_order_no),
    CONSTRAINT fk_work_order_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_work_order_creator FOREIGN KEY (creator_id) REFERENCES sys_user(id),
    CONSTRAINT fk_work_order_handler FOREIGN KEY (handler_id) REFERENCES sys_user(id),
    INDEX idx_work_order_project_status (project_id, status),
    INDEX idx_work_order_handler_status (handler_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_order_device (
    work_order_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (work_order_id, device_id),
    CONSTRAINT fk_work_order_device_order FOREIGN KEY (work_order_id) REFERENCES work_order(id),
    CONSTRAINT fk_work_order_device_device FOREIGN KEY (device_id) REFERENCES device(id),
    INDEX idx_work_order_device_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_order_track (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    operator_id BIGINT,
    operator_type VARCHAR(16) NOT NULL DEFAULT 'USER',
    action VARCHAR(32) NOT NULL,
    before_status VARCHAR(24),
    after_status VARCHAR(24),
    remark VARCHAR(1000),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_track_work_order FOREIGN KEY (work_order_id) REFERENCES work_order(id),
    INDEX idx_track_work_order_time (work_order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_user (id, username, password_hash, display_name, status)
VALUES (1, 'admin', '{BOOTSTRAP}', 'Platform Admin', 'ENABLED');

INSERT INTO project (id, project_code, project_name, description)
VALUES (1, 'SMART-CAMPUS', 'Smart Campus Delivery', 'Demonstration project for campus environmental sensors');

INSERT INTO product (id, product_code, product_name, model, communication_type)
VALUES (1, 'ENV-SENSOR', 'Environment Sensor', 'ES-100', 'MQTT');

INSERT INTO device (id, sn, device_name, project_id, product_id, online_status)
VALUES
    (1, 'DEMO-SN-001', 'North Building Sensor', 1, 1, 'ONLINE'),
    (2, 'DEMO-SN-002', 'South Building Sensor', 1, 1, 'OFFLINE');

