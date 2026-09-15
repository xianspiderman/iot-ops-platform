package io.github.xianspiderman.iotops.workorder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
    @Update("""
            UPDATE work_order
               SET status = 'PROCESSING', handler_id = #{operatorId}, accept_time = #{acceptTime}
             WHERE id = #{workOrderId} AND status = 'WAITING'
            """)
    int acceptIfWaiting(@Param("workOrderId") Long workOrderId,
                        @Param("operatorId") Long operatorId,
                        @Param("acceptTime") LocalDateTime acceptTime);
}
