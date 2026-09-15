package io.github.xianspiderman.iotops.workorder;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderViewJsonTest {
    @Test
    void exposesParentFieldsAndRelationIdsInOneListRow() throws Exception {
        WorkOrder order = new WorkOrder();
        order.setId(7L);
        order.setWorkOrderNo("WO-TEST-007");
        order.setStatus("WAITING");

        String json = JsonMapper.builder().findAndAddModules().build()
                .writeValueAsString(new WorkOrderView(order, List.of(10L, 11L)));

        assertThat(json).contains("\"workOrderNo\":\"WO-TEST-007\"")
                .contains("\"deviceIds\":[10,11]")
                .doesNotContain("\"workOrder\"");
    }
}
