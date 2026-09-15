package io.github.xianspiderman.iotops.timeout;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("timeout_job_execution")
public class TimeoutJobExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String executionKey;
    private String triggerSource;
    private String status;
    private Integer scannedCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failureCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
