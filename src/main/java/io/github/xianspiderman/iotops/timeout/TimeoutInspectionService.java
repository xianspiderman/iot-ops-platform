package io.github.xianspiderman.iotops.timeout;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.workorder.WorkOrder;
import io.github.xianspiderman.iotops.workorder.WorkOrderMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeoutInspectionService {
    private final WorkOrderMapper workOrderMapper;
    private final TimeoutItemService itemService;
    private final TimeoutFailureService failureService;
    private final TimeoutJobExecutionMapper executionMapper;
    private final TimeoutFailureMapper failureMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Value("${iot-ops.timeout.batch-size:100}")
    private int batchSize;

    public TimeoutJobExecution run(String triggerSource) {
        LocalDateTime now = LocalDateTime.now(clock);
        TimeoutJobExecution execution = new TimeoutJobExecution();
        execution.setExecutionKey(UUID.randomUUID().toString());
        execution.setTriggerSource(triggerSource);
        execution.setStatus("RUNNING");
        execution.setScannedCount(0);
        execution.setSuccessCount(0);
        execution.setSkippedCount(0);
        execution.setFailureCount(0);
        execution.setStartedAt(now);
        executionMapper.insert(execution);

        List<Long> candidates = workOrderMapper.selectTimeoutCandidates(now, Math.max(1, Math.min(batchSize, 1000)));
        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (Long workOrderId : candidates) {
            try {
                if (itemService.markOne(workOrderId, null, "SYSTEM", "Deadline exceeded during scheduled inspection")) {
                    success++;
                    resolvePendingFailure(workOrderId);
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
                failureService.record(workOrderId, execution.getId(), rootMessage(exception));
                log.error("timeout_inspection_item_failed executionKey={} workOrderId={}",
                        execution.getExecutionKey(), workOrderId, exception);
            }
        }

        execution.setStatus(failed == 0 ? "SUCCESS" : "PARTIAL_FAILED");
        execution.setScannedCount(candidates.size());
        execution.setSuccessCount(success);
        execution.setSkippedCount(skipped);
        execution.setFailureCount(failed);
        execution.setFinishedAt(LocalDateTime.now(clock));
        executionMapper.updateById(execution);
        meterRegistry.counter("iot.timeout.inspection", "status", execution.getStatus()).increment();
        meterRegistry.counter("iot.timeout.items", "outcome", "marked").increment(success);
        meterRegistry.counter("iot.timeout.items", "outcome", "skipped").increment(skipped);
        meterRegistry.counter("iot.timeout.items", "outcome", "failed").increment(failed);
        log.info("timeout_inspection_completed executionKey={} scanned={} success={} skipped={} failed={}",
                execution.getExecutionKey(), candidates.size(), success, skipped, failed);
        return execution;
    }

    public void compensate(Long failureId, Long operatorId) {
        TimeoutFailure failure = failureMapper.selectById(failureId);
        if (failure == null) {
            throw new BusinessException("TIMEOUT_FAILURE_NOT_FOUND", "Timeout failure does not exist");
        }
        if ("RESOLVED".equals(failure.getStatus())) {
            return;
        }
        boolean marked = itemService.markOne(failure.getWorkOrderId(), operatorId, "USER",
                "Timeout compensation requested by user " + operatorId);
        WorkOrder order = workOrderMapper.selectById(failure.getWorkOrderId());
        if (!marked && order != null && !Boolean.TRUE.equals(order.getTimeoutFlag())
                && !List.of("CLOSED", "CANCELED").contains(order.getStatus())) {
            throw new BusinessException("WORK_ORDER_NOT_TIMEOUT_CANDIDATE",
                    "Work order is not currently eligible for timeout marking");
        }
        failureService.resolve(failureId, operatorId);
    }

    private void resolvePendingFailure(Long workOrderId) {
        TimeoutFailure pending = failureMapper.selectOne(Wrappers.<TimeoutFailure>lambdaQuery()
                .eq(TimeoutFailure::getWorkOrderId, workOrderId)
                .eq(TimeoutFailure::getStatus, "PENDING"));
        if (pending != null) {
            failureService.resolve(pending.getId(), null);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
