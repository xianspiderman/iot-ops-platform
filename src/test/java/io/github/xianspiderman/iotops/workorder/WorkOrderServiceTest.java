package io.github.xianspiderman.iotops.workorder;

import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import io.github.xianspiderman.iotops.project.Project;
import io.github.xianspiderman.iotops.project.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderDeviceMapper relationMapper;
    @Mock
    private WorkOrderTrackMapper trackMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private ProjectMapper projectMapper;

    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new WorkOrderService(workOrderMapper, relationMapper, trackMapper, deviceMapper,
                projectMapper, clock);
    }

    @Test
    void createsWaitingOrderWithDeduplicatedDeviceRelationsAndTrack() {
        when(projectMapper.selectById(1L)).thenReturn(new Project());
        Device first = device(10L, 1L);
        Device second = device(11L, 1L);
        when(deviceMapper.selectByIds(org.mockito.ArgumentMatchers.<Long>anyCollection()))
                .thenReturn(List.of(first, second));
        doAnswer(invocation -> {
            WorkOrder order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        }).when(workOrderMapper).insert(any(WorkOrder.class));
        when(workOrderMapper.selectById(99L)).thenAnswer(invocation -> {
            WorkOrder order = new WorkOrder();
            order.setId(99L);
            order.setStatus(WorkOrderStatus.WAITING.name());
            return order;
        });

        WorkOrder created = service.create(new WorkOrderService.CreateCommand(
                1L, List.of(10L, 11L, 10L), "Gateway offline", "Two devices are unreachable", "HIGH"), 7L);

        assertThat(created.getStatus()).isEqualTo("WAITING");
        verify(relationMapper, org.mockito.Mockito.times(2)).insert(any(WorkOrderDevice.class));
        verify(trackMapper).insert(any(WorkOrderTrack.class));
    }

    @Test
    void rejectsDeviceFromAnotherProjectBeforeWriting() {
        when(projectMapper.selectById(1L)).thenReturn(new Project());
        when(deviceMapper.selectByIds(org.mockito.ArgumentMatchers.<Long>anyCollection()))
                .thenReturn(List.of(device(10L, 2L)));

        assertThatThrownBy(() -> service.create(new WorkOrderService.CreateCommand(
                1L, List.of(10L), "Problem", "Description", "NORMAL"), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("belong");

        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
    }

    @Test
    void failedConditionalAcceptDoesNotWriteTrack() {
        when(workOrderMapper.acceptIfWaiting(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.accept(99L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("accepted");

        verify(trackMapper, never()).insert(any(WorkOrderTrack.class));
    }

    private Device device(Long id, Long projectId) {
        Device device = new Device();
        device.setId(id);
        device.setProjectId(projectId);
        return device;
    }
}
