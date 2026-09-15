package io.github.xianspiderman.iotops.alarm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm")
public class Alarm {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long deviceId;
    private String eventType;
    private String severity;
    private LocalDateTime occurredAt;
    private String payload;
    private String status;
    private LocalDateTime createdAt;
}
