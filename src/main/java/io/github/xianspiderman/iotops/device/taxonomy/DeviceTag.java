package io.github.xianspiderman.iotops.device.taxonomy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_tag")
public class DeviceTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tagName;
    private String tagColor;
    private LocalDateTime createdAt;
}
