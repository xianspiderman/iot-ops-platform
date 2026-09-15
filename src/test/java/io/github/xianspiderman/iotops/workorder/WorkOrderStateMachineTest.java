package io.github.xianspiderman.iotops.workorder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderStateMachineTest {
    @Test
    void supportsMainFlowAndCancellationSidePath() {
        assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.WAITING,
                WorkOrderStatus.PROCESSING)).isTrue();
        assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.PROCESSING,
                WorkOrderStatus.WAIT_VERIFY)).isTrue();
        assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.WAIT_VERIFY,
                WorkOrderStatus.CLOSED)).isTrue();
        assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.WAITING,
                WorkOrderStatus.CANCELED)).isTrue();
        assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.PROCESSING,
                WorkOrderStatus.CANCELED)).isTrue();
        assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.WAIT_VERIFY,
                WorkOrderStatus.CANCELED)).isTrue();
    }

    @Test
    void terminalStatesCannotTransition() {
        for (WorkOrderStatus target : WorkOrderStatus.values()) {
            assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.CLOSED, target)).isFalse();
            assertThat(WorkOrderStateMachine.canTransition(WorkOrderStatus.CANCELED, target)).isFalse();
        }
    }
}
