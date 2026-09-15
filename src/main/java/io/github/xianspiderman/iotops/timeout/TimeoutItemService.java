package io.github.xianspiderman.iotops.timeout;

import io.github.xianspiderman.iotops.workorder.WorkOrderMapper;
import io.github.xianspiderman.iotops.workorder.WorkOrderTrack;
import io.github.xianspiderman.iotops.workorder.WorkOrderTrackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TimeoutItemService {
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderTrackMapper trackMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markOne(Long workOrderId, Long operatorId, String operatorType, String remark) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (workOrderMapper.markTimeoutIfCandidate(workOrderId, now) != 1) {
            return false;
        }
        WorkOrderTrack track = new WorkOrderTrack();
        track.setWorkOrderId(workOrderId);
        track.setOperatorId(operatorId);
        track.setOperatorType(operatorType);
        track.setAction("TIMEOUT_MARK");
        track.setRemark(remark);
        trackMapper.insert(track);
        return true;
    }
}
