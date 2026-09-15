package io.github.xianspiderman.iotops.workorder;

import io.github.xianspiderman.iotops.common.BusinessException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class WorkOrderStateMachine {
    private static final Map<WorkOrderStatus, EnumSet<WorkOrderStatus>> TRANSITIONS =
            new EnumMap<>(WorkOrderStatus.class);

    static {
        TRANSITIONS.put(WorkOrderStatus.WAITING,
                EnumSet.of(WorkOrderStatus.PROCESSING, WorkOrderStatus.CANCELED));
        TRANSITIONS.put(WorkOrderStatus.PROCESSING,
                EnumSet.of(WorkOrderStatus.WAIT_VERIFY, WorkOrderStatus.CANCELED));
        TRANSITIONS.put(WorkOrderStatus.WAIT_VERIFY,
                EnumSet.of(WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELED));
        TRANSITIONS.put(WorkOrderStatus.CLOSED, EnumSet.noneOf(WorkOrderStatus.class));
        TRANSITIONS.put(WorkOrderStatus.CANCELED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    private WorkOrderStateMachine() {
    }

    public static boolean canTransition(WorkOrderStatus from, WorkOrderStatus to) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(WorkOrderStatus.class)).contains(to);
    }

    public static void requireTransition(String from, WorkOrderStatus to) {
        WorkOrderStatus source;
        try {
            source = WorkOrderStatus.valueOf(from);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_WORK_ORDER_STATE", "Unknown work order state: " + from);
        }
        if (!canTransition(source, to)) {
            throw new BusinessException("INVALID_WORK_ORDER_TRANSITION",
                    "Cannot move work order from " + source + " to " + to);
        }
    }
}
