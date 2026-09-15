package io.github.xianspiderman.iotops.workorder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
    @Select("""
            SELECT id FROM work_order
             WHERE timeout_flag = 0 AND deadline_time < #{now}
               AND status NOT IN ('CLOSED', 'CANCELED')
             ORDER BY deadline_time, id
             LIMIT #{batchSize}
            """)
    List<Long> selectTimeoutCandidates(@Param("now") LocalDateTime now,
                                       @Param("batchSize") int batchSize);

    @Update("""
            UPDATE work_order SET timeout_flag = 1, timeout_time = #{timeoutTime}
             WHERE id = #{workOrderId} AND timeout_flag = 0 AND deadline_time < #{timeoutTime}
               AND status NOT IN ('CLOSED', 'CANCELED')
            """)
    int markTimeoutIfCandidate(@Param("workOrderId") Long workOrderId,
                               @Param("timeoutTime") LocalDateTime timeoutTime);

    @Update("""
            UPDATE work_order
               SET status = 'PROCESSING', handler_id = #{operatorId}, accept_time = #{acceptTime}
             WHERE id = #{workOrderId} AND status = 'WAITING'
            """)
    int acceptIfWaiting(@Param("workOrderId") Long workOrderId,
                        @Param("operatorId") Long operatorId,
                        @Param("acceptTime") LocalDateTime acceptTime);

    @Update("""
            UPDATE work_order
               SET status = 'WAIT_VERIFY', solution = #{solution}, submit_time = #{submitTime}
             WHERE id = #{workOrderId} AND status = 'PROCESSING' AND handler_id = #{operatorId}
            """)
    int submitIfProcessingByHandler(@Param("workOrderId") Long workOrderId,
                                    @Param("operatorId") Long operatorId,
                                    @Param("solution") String solution,
                                    @Param("submitTime") LocalDateTime submitTime);

    @Update("""
            UPDATE work_order
               SET status = 'CLOSED', close_time = #{closeTime}
             WHERE id = #{workOrderId} AND status = 'WAIT_VERIFY'
            """)
    int closeIfWaitingVerify(@Param("workOrderId") Long workOrderId,
                             @Param("closeTime") LocalDateTime closeTime);

    @Update("""
            UPDATE work_order
               SET status = 'CANCELED', cancel_time = #{cancelTime}
             WHERE id = #{workOrderId} AND status = #{beforeStatus}
               AND status IN ('WAITING', 'PROCESSING', 'WAIT_VERIFY')
            """)
    int cancelIfCurrent(@Param("workOrderId") Long workOrderId,
                        @Param("beforeStatus") String beforeStatus,
                        @Param("cancelTime") LocalDateTime cancelTime);

    @Update("""
            UPDATE work_order
               SET handler_id = #{newHandlerId}
             WHERE id = #{workOrderId} AND status = 'PROCESSING' AND handler_id = #{currentHandlerId}
            """)
    int transferIfProcessingByHandler(@Param("workOrderId") Long workOrderId,
                                      @Param("currentHandlerId") Long currentHandlerId,
                                      @Param("newHandlerId") Long newHandlerId);
}
