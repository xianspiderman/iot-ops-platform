package io.github.xianspiderman.iotops.workorder;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_device")
public class WorkOrderDevice {
    private Long workOrderId;
    private Long deviceId;
    private LocalDateTime createdAt;
}

