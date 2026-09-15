USE xxl_job;

INSERT INTO xxl_job_group
    (id, app_name, title, address_type, address_list, update_time)
VALUES
    (10, 'iot-ops-platform', 'IoT Ops Platform executor', 0, NULL, NOW());

INSERT INTO xxl_job_info
    (id, job_group, job_desc, add_time, update_time, author, alarm_email,
     schedule_type, schedule_conf, misfire_strategy, executor_route_strategy,
     executor_handler, executor_param, executor_block_strategy, executor_timeout,
     executor_fail_retry_count, glue_type, glue_source, glue_remark, glue_updatetime,
     child_jobid, trigger_status, trigger_last_time, trigger_next_time)
VALUES
    (10, 10, 'Work order timeout inspection', NOW(), NOW(), 'iot-ops', '',
     'CRON', '0 */5 * * * ?', 'DO_NOTHING', 'FIRST',
     'workOrderTimeoutInspection', '', 'SERIAL_EXECUTION', 120,
     0, 'BEAN', '', 'Initialized by IoT Ops Platform', NOW(),
     '', 1, 0, UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 5 MINUTE)) * 1000);
