package io.github.xianspiderman.iotops.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order_track")
public class WorkOrderTrack {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long operatorId;
    private String operatorType;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String remark;
    private LocalDateTime createdAt;
}

