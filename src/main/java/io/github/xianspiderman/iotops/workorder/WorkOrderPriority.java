package io.github.xianspiderman.iotops.workorder;

public enum WorkOrderPriority {
    HIGH(4),
    NORMAL(12),
    LOW(36);

    private final int deadlineHours;

    WorkOrderPriority(int deadlineHours) {
        this.deadlineHours = deadlineHours;
    }

    public int deadlineHours() {
        return deadlineHours;
    }
}

