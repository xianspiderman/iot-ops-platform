package io.github.xianspiderman.iotops.device.importer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_import_error")
public class DeviceImportError {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer rowNo;
    private String sn;
    private String rawData;
    private String errorMessage;
    private LocalDateTime createdAt;
}
