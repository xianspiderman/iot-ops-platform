package io.github.xianspiderman.iotops.device.importer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_import_task")
public class DeviceImportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String status;
    private Integer totalRows;
    private Integer successRows;
    private Integer errorRows;
    private String failureReason;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
