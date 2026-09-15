package io.github.xianspiderman.iotops.device.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceImportTaskStateService {
    private final DeviceImportTaskMapper taskMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeviceImportTask start(String fileName, Long operatorId) {
        DeviceImportTask task = new DeviceImportTask();
        task.setFileName(fileName);
        task.setStatus(DeviceImportStatus.PROCESSING.name());
        task.setCreatedBy(operatorId);
        task.setStartedAt(LocalDateTime.now(clock));
        taskMapper.insert(task);
        return task;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long taskId, String reason) {
        DeviceImportTask task = new DeviceImportTask();
        task.setId(taskId);
        task.setStatus(DeviceImportStatus.FAILED.name());
        task.setFailureReason(abbreviate(reason));
        task.setFinishedAt(LocalDateTime.now(clock));
        taskMapper.updateById(task);
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "Unexpected import failure";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
