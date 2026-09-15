package io.github.xianspiderman.iotops.workorder;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkOrderDeviceMapper {
    @Insert("INSERT INTO work_order_device(work_order_id, device_id) VALUES(#{workOrderId}, #{deviceId})")
    int insert(WorkOrderDevice relation);

    @Select("""
            <script>
            SELECT work_order_id, device_id, created_at
              FROM work_order_device
             WHERE work_order_id IN
             <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             ORDER BY work_order_id, device_id
            </script>
            """)
    List<WorkOrderDevice> selectByWorkOrderIds(@Param("ids") List<Long> ids);
}
