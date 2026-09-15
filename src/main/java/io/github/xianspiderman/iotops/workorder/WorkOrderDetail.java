package io.github.xianspiderman.iotops.workorder;

import java.util.List;

public record WorkOrderDetail(WorkOrder workOrder, List<Long> deviceIds, List<WorkOrderTrack> tracks) {
}
