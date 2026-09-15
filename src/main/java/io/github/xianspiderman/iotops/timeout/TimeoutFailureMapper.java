package io.github.xianspiderman.iotops.timeout;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface TimeoutFailureMapper extends BaseMapper<TimeoutFailure> {
    @Insert("""
            INSERT INTO timeout_failure(work_order_id, execution_id, status, failure_reason,
                                        attempt_count, last_failed_at)
            VALUES(#{workOrderId}, #{executionId}, 'PENDING', #{reason}, 1, #{failedAt})
            ON DUPLICATE KEY UPDATE execution_id = VALUES(execution_id), status = 'PENDING',
              failure_reason = VALUES(failure_reason), attempt_count = attempt_count + 1,
              last_failed_at = VALUES(last_failed_at), resolved_at = NULL, resolved_by = NULL
            """)
    int upsertFailure(@Param("workOrderId") Long workOrderId,
                      @Param("executionId") Long executionId,
                      @Param("reason") String reason,
                      @Param("failedAt") LocalDateTime failedAt);

    @Update("""
            UPDATE timeout_failure SET status = 'RESOLVED', resolved_at = #{resolvedAt}, resolved_by = #{operatorId}
             WHERE id = #{id} AND status = 'PENDING'
            """)
    int resolve(@Param("id") Long id, @Param("operatorId") Long operatorId,
                @Param("resolvedAt") LocalDateTime resolvedAt);
}
