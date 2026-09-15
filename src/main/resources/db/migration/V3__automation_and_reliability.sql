ALTER TABLE work_order
    ADD COLUMN timeout_flag TINYINT(1) NOT NULL DEFAULT 0 AFTER deadline_time,
    ADD COLUMN timeout_time DATETIME(3) NULL AFTER timeout_flag,
    ADD INDEX idx_work_order_timeout_candidate (timeout_flag, deadline_time, status, id);

CREATE TABLE alarm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(96) NOT NULL,
    device_id BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_alarm_event_id UNIQUE (event_id),
    CONSTRAINT fk_alarm_device FOREIGN KEY (device_id) REFERENCES device(id),
    INDEX idx_alarm_device_time (device_id, occurred_at),
    INDEX idx_alarm_status_time (status, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE alarm_event_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(96) NOT NULL,
    raw_payload JSON NOT NULL,
    corrected_payload JSON,
    process_status VARCHAR(24) NOT NULL,
    failure_category VARCHAR(32),
    failure_reason VARCHAR(1000),
    alarm_id BIGINT,
    attempt_count INT NOT NULL DEFAULT 1,
    confirmed_by BIGINT,
    confirmed_at DATETIME(3),
    processed_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_alarm_event_record_event UNIQUE (event_id),
    CONSTRAINT fk_alarm_event_alarm FOREIGN KEY (alarm_id) REFERENCES alarm(id),
    CONSTRAINT fk_alarm_event_confirmer FOREIGN KEY (confirmed_by) REFERENCES sys_user(id),
    INDEX idx_alarm_event_status_time (process_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE timeout_job_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_key VARCHAR(64) NOT NULL,
    trigger_source VARCHAR(24) NOT NULL,
    status VARCHAR(20) NOT NULL,
    scanned_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_timeout_execution_key UNIQUE (execution_key),
    INDEX idx_timeout_execution_time (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE timeout_failure (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    execution_id BIGINT,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(1000) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    last_failed_at DATETIME(3) NOT NULL,
    resolved_at DATETIME(3),
    resolved_by BIGINT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_timeout_failure_order UNIQUE (work_order_id),
    CONSTRAINT fk_timeout_failure_order FOREIGN KEY (work_order_id) REFERENCES work_order(id),
    CONSTRAINT fk_timeout_failure_execution FOREIGN KEY (execution_id) REFERENCES timeout_job_execution(id),
    CONSTRAINT fk_timeout_failure_resolver FOREIGN KEY (resolved_by) REFERENCES sys_user(id),
    INDEX idx_timeout_failure_status_time (status, last_failed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
