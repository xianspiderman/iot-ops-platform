package io.github.xianspiderman.iotops.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workOrderNo;
    private Long projectId;
    private String title;
    private String description;
    private String priority;
    private String status;
    private Long creatorId;
    private Long handlerId;
    private String solution;
    private LocalDateTime deadlineTime;
    private Boolean timeoutFlag;
    private LocalDateTime timeoutTime;
    private LocalDateTime acceptTime;
    private LocalDateTime submitTime;
    private LocalDateTime closeTime;
    private LocalDateTime cancelTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
