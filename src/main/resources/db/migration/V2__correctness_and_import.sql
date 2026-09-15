ALTER TABLE work_order
    ADD COLUMN submit_time DATETIME(3) NULL AFTER accept_time,
    ADD COLUMN cancel_time DATETIME(3) NULL AFTER close_time,
    ADD INDEX idx_work_order_status_deadline (status, deadline_time, id);

CREATE TABLE device_import_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000),
    created_by BIGINT NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_import_task_user FOREIGN KEY (created_by) REFERENCES sys_user(id),
    INDEX idx_import_task_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE device_import_error (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    sn VARCHAR(64),
    raw_data JSON,
    error_message VARCHAR(1000) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_import_error_task FOREIGN KEY (task_id) REFERENCES device_import_task(id),
    INDEX idx_import_error_task_row (task_id, row_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
