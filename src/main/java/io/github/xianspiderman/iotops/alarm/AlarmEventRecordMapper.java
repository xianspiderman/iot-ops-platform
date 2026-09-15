package io.github.xianspiderman.iotops.alarm;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface AlarmEventRecordMapper extends BaseMapper<AlarmEventRecord> {
    @Insert("""
            INSERT INTO alarm_event_record(event_id, raw_payload, process_status, failure_category,
                                           failure_reason, attempt_count)
            VALUES(#{eventId}, CAST(#{rawPayload} AS JSON), #{processStatus}, #{category}, #{reason}, 1)
            ON DUPLICATE KEY UPDATE
              raw_payload = IF(process_status = 'PROCESSED', raw_payload, VALUES(raw_payload)),
              process_status = IF(process_status = 'PROCESSED', process_status, VALUES(process_status)),
              failure_category = IF(process_status = 'PROCESSED', failure_category, VALUES(failure_category)),
              failure_reason = IF(process_status = 'PROCESSED', failure_reason, VALUES(failure_reason)),
              attempt_count = attempt_count + IF(process_status = 'PROCESSED', 0, 1)
            """)
    int upsertFailure(@Param("eventId") String eventId,
                      @Param("rawPayload") String rawPayload,
                      @Param("processStatus") String processStatus,
                      @Param("category") String category,
                      @Param("reason") String reason);

    @Update("""
            UPDATE alarm_event_record
               SET corrected_payload = CAST(#{payload} AS JSON), process_status = 'CONFIRMED',
                   confirmed_by = #{operatorId}, confirmed_at = #{confirmedAt}, failure_reason = NULL
             WHERE id = #{id} AND process_status IN ('BUSINESS_BAD_MESSAGE', 'SYSTEM_FAILURE', 'CONFIRMED')
            """)
    int confirm(@Param("id") Long id, @Param("payload") String payload,
                @Param("operatorId") Long operatorId, @Param("confirmedAt") LocalDateTime confirmedAt);

    @Update("""
            UPDATE alarm_event_record
               SET process_status = 'PROCESSED', failure_category = NULL, failure_reason = NULL,
                   alarm_id = #{alarmId}, processed_at = #{processedAt}, attempt_count = attempt_count + 1
             WHERE id = #{id} AND process_status <> 'PROCESSED'
            """)
    int markProcessed(@Param("id") Long id, @Param("alarmId") Long alarmId,
                      @Param("processedAt") LocalDateTime processedAt);
}
