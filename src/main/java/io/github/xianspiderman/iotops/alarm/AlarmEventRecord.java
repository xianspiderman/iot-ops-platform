package io.github.xianspiderman.iotops.alarm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm_event_record")
public class AlarmEventRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String rawPayload;
    private String correctedPayload;
    private String processStatus;
    private String failureCategory;
    private String failureReason;
    private Long alarmId;
    private Integer attemptCount;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
