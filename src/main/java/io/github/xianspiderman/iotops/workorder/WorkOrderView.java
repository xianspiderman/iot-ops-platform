package io.github.xianspiderman.iotops.workorder;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

public record WorkOrderView(@JsonUnwrapped WorkOrder workOrder, List<Long> deviceIds) {
}
