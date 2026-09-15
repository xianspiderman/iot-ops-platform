package io.github.xianspiderman.iotops.workorder;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import io.github.xianspiderman.iotops.project.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class WorkOrderService {
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderDeviceMapper relationMapper;
    private final WorkOrderTrackMapper trackMapper;
    private final DeviceMapper deviceMapper;
    private final ProjectMapper projectMapper;
    private final Clock clock;

    public PageResult<WorkOrder> page(long page, long size, String status) {
        IPage<WorkOrder> result = workOrderMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<WorkOrder>lambdaQuery()
                        .eq(status != null && !status.isBlank(), WorkOrder::getStatus, status)
                        .orderByDesc(WorkOrder::getId));
        return PageResult.from(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkOrder create(CreateCommand command, Long operatorId) {
        if (projectMapper.selectById(command.projectId()) == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "Project does not exist");
        }
        LinkedHashSet<Long> deviceIds = new LinkedHashSet<>(command.deviceIds());
        if (deviceIds.isEmpty()) {
            throw new BusinessException("DEVICE_REQUIRED", "At least one device is required");
        }
        List<Device> devices = deviceMapper.selectByIds(deviceIds);
        if (devices.size() != deviceIds.size()) {
            throw new BusinessException("DEVICE_NOT_FOUND", "One or more devices do not exist");
        }
        if (devices.stream().anyMatch(device -> !command.projectId().equals(device.getProjectId()))) {
            throw new BusinessException("DEVICE_PROJECT_MISMATCH", "All devices must belong to the work order project");
        }

        WorkOrderPriority priority = parsePriority(command.priority());
        LocalDateTime now = LocalDateTime.now(clock);
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo(generateNo(now));
        order.setProjectId(command.projectId());
        order.setTitle(command.title().trim());
        order.setDescription(command.description().trim());
        order.setPriority(priority.name());
        order.setStatus(WorkOrderStatus.WAITING.name());
        order.setCreatorId(operatorId);
        order.setDeadlineTime(now.plusHours(priority.deadlineHours()));
        workOrderMapper.insert(order);

        for (Long deviceId : deviceIds) {
            WorkOrderDevice relation = new WorkOrderDevice();
            relation.setWorkOrderId(order.getId());
            relation.setDeviceId(deviceId);
            relationMapper.insert(relation);
        }
        addTrack(order.getId(), operatorId, "USER", "CREATE", null,
                WorkOrderStatus.WAITING.name(), "Work order created");
        return workOrderMapper.selectById(order.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void accept(Long workOrderId, Long operatorId) {
        int affected = workOrderMapper.acceptIfWaiting(workOrderId, operatorId, LocalDateTime.now(clock));
        if (affected != 1) {
            throw new BusinessException("WORK_ORDER_NOT_WAITING", "Work order was accepted or is not waiting");
        }
        addTrack(workOrderId, operatorId, "USER", "ACCEPT", WorkOrderStatus.WAITING.name(),
                WorkOrderStatus.PROCESSING.name(), "Work order accepted");
    }

    private WorkOrderPriority parsePriority(String value) {
        try {
            return WorkOrderPriority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_PRIORITY", "Priority must be HIGH, NORMAL or LOW");
        }
    }

    private String generateNo(LocalDateTime time) {
        return "WO-" + time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-"
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }

    private void addTrack(Long orderId, Long operatorId, String operatorType, String action,
                          String before, String after, String remark) {
        WorkOrderTrack track = new WorkOrderTrack();
        track.setWorkOrderId(orderId);
        track.setOperatorId(operatorId);
        track.setOperatorType(operatorType);
        track.setAction(action);
        track.setBeforeStatus(before);
        track.setAfterStatus(after);
        track.setRemark(remark);
        trackMapper.insert(track);
    }

    public record CreateCommand(Long projectId, List<Long> deviceIds, String title,
                                String description, String priority) {
    }
}
