package io.github.xianspiderman.iotops.timeout;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("timeout_failure")
public class TimeoutFailure {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workOrderId;
    private Long executionId;
    private String status;
    private String failureReason;
    private Integer attemptCount;
    private LocalDateTime lastFailedAt;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
