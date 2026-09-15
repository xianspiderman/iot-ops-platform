package io.github.xianspiderman.iotops.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sn;
    private String deviceName;
    private Long projectId;
    private Long productId;
    private String imei;
    private String mac;
    private String firmwareVersion;
    private String onlineStatus;
    private LocalDateTime lastCommunicationTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

