package io.github.xianspiderman.iotops.device.importer;

import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceImportWriteService {
    private final DeviceMapper deviceMapper;
    private final DeviceImportErrorMapper errorMapper;
    private final DeviceImportTaskMapper taskMapper;
    private final Clock clock;

    @Value("${iot-ops.import.insert-batch-size:200}")
    private int insertBatchSize;

    @Transactional(rollbackFor = Exception.class)
    public void persist(Long taskId, int totalRows, List<Device> validDevices, List<DeviceImportError> errors) {
        for (int start = 0; start < validDevices.size(); start += insertBatchSize) {
            int end = Math.min(start + insertBatchSize, validDevices.size());
            deviceMapper.batchInsert(validDevices.subList(start, end));
        }
        for (DeviceImportError error : errors) {
            error.setTaskId(taskId);
            errorMapper.insert(error);
        }

        DeviceImportTask task = new DeviceImportTask();
        task.setId(taskId);
        task.setTotalRows(totalRows);
        task.setSuccessRows(validDevices.size());
        task.setErrorRows(errors.size());
        task.setStatus(errors.isEmpty() ? DeviceImportStatus.SUCCESS.name()
                : validDevices.isEmpty() ? DeviceImportStatus.FAILED.name()
                : DeviceImportStatus.PARTIAL_FAILED.name());
        task.setFinishedAt(LocalDateTime.now(clock));
        taskMapper.updateById(task);
    }
}
