package io.github.xianspiderman.iotops.device.taxonomy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_group")
public class DeviceGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String groupName;
    private String description;
    private LocalDateTime createdAt;
}
