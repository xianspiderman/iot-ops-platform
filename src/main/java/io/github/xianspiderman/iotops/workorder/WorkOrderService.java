package io.github.xianspiderman.iotops.workorder;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.auth.SysUser;
import io.github.xianspiderman.iotops.auth.SysUserMapper;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class WorkOrderService {
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderDeviceMapper relationMapper;
    private final WorkOrderTrackMapper trackMapper;
    private final DeviceMapper deviceMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final Clock clock;

    public PageResult<WorkOrderView> page(long page, long size, String status) {
        return page(page, size, status, DataScope.all());
    }

    public PageResult<WorkOrderView> page(long page, long size, String status, DataScope scope) {
        if (!scope.allProjects() && scope.projectIds().isEmpty()) {
            return new PageResult<>(page, Math.min(size, 100), 0, List.of());
        }
        IPage<WorkOrder> result = workOrderMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<WorkOrder>lambdaQuery()
                        .eq(status != null && !status.isBlank(), WorkOrder::getStatus, status)
                        .in(!scope.allProjects(), WorkOrder::getProjectId, scope.projectIds())
                        .orderByDesc(WorkOrder::getId));
        List<Long> orderIds = result.getRecords().stream().map(WorkOrder::getId).toList();
        Map<Long, List<Long>> deviceIdsByOrder = orderIds.isEmpty()
                ? Map.of()
                : relationMapper.selectByWorkOrderIds(orderIds).stream().collect(Collectors.groupingBy(
                        WorkOrderDevice::getWorkOrderId,
                        Collectors.mapping(WorkOrderDevice::getDeviceId, Collectors.toList())));
        List<WorkOrderView> records = result.getRecords().stream()
                .map(order -> new WorkOrderView(order, deviceIdsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    public WorkOrderDetail detail(Long workOrderId) {
        WorkOrder order = requireOrder(workOrderId);
        List<Long> deviceIds = relationMapper.selectByWorkOrderIds(List.of(workOrderId)).stream()
                .map(WorkOrderDevice::getDeviceId).toList();
        List<WorkOrderTrack> tracks = trackMapper.selectList(Wrappers.<WorkOrderTrack>lambdaQuery()
                .eq(WorkOrderTrack::getWorkOrderId, workOrderId)
                .orderByAsc(WorkOrderTrack::getId));
        return new WorkOrderDetail(order, deviceIds, tracks);
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

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long workOrderId, Long operatorId, String solution) {
        WorkOrderStateMachine.requireTransition(WorkOrderStatus.PROCESSING.name(), WorkOrderStatus.WAIT_VERIFY);
        int affected = workOrderMapper.submitIfProcessingByHandler(workOrderId, operatorId, solution.trim(),
                LocalDateTime.now(clock));
        if (affected != 1) {
            throw new BusinessException("WORK_ORDER_SUBMIT_CONFLICT",
                    "Only the current handler can submit a processing work order");
        }
        addTrack(workOrderId, operatorId, "USER", "SUBMIT", WorkOrderStatus.PROCESSING.name(),
                WorkOrderStatus.WAIT_VERIFY.name(), "Work order submitted for verification");
    }

    @Transactional(rollbackFor = Exception.class)
    public void verifyAndClose(Long workOrderId, Long operatorId, String remark) {
        WorkOrderStateMachine.requireTransition(WorkOrderStatus.WAIT_VERIFY.name(), WorkOrderStatus.CLOSED);
        int affected = workOrderMapper.closeIfWaitingVerify(workOrderId, LocalDateTime.now(clock));
        if (affected != 1) {
            throw new BusinessException("WORK_ORDER_VERIFY_CONFLICT",
                    "Work order is not waiting for verification");
        }
        addTrack(workOrderId, operatorId, "USER", "VERIFY_CLOSE", WorkOrderStatus.WAIT_VERIFY.name(),
                WorkOrderStatus.CLOSED.name(), remark == null || remark.isBlank() ? "Verified and closed" : remark.trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long workOrderId, Long operatorId, String reason) {
        WorkOrder order = requireOrder(workOrderId);
        WorkOrderStateMachine.requireTransition(order.getStatus(), WorkOrderStatus.CANCELED);
        int affected = workOrderMapper.cancelIfCurrent(workOrderId, order.getStatus(), LocalDateTime.now(clock));
        if (affected != 1) {
            throw new BusinessException("WORK_ORDER_CANCEL_CONFLICT", "Work order state changed; refresh and retry");
        }
        addTrack(workOrderId, operatorId, "USER", "CANCEL", order.getStatus(),
                WorkOrderStatus.CANCELED.name(), reason.trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long workOrderId, Long operatorId, Long newHandlerId, String reason) {
        SysUser newHandler = userMapper.selectById(newHandlerId);
        if (newHandler == null || !"ENABLED".equals(newHandler.getStatus())) {
            throw new BusinessException("HANDLER_NOT_AVAILABLE", "Target handler does not exist or is disabled");
        }
        int affected = workOrderMapper.transferIfProcessingByHandler(workOrderId, operatorId, newHandlerId);
        if (affected != 1) {
            throw new BusinessException("WORK_ORDER_TRANSFER_CONFLICT",
                    "Only the current handler can transfer a processing work order");
        }
        addTrack(workOrderId, operatorId, "USER", "TRANSFER", WorkOrderStatus.PROCESSING.name(),
                WorkOrderStatus.PROCESSING.name(), "Transferred to user " + newHandlerId + ": " + reason.trim());
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

    private WorkOrder requireOrder(Long workOrderId) {
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException("WORK_ORDER_NOT_FOUND", "Work order does not exist");
        }
        return order;
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
