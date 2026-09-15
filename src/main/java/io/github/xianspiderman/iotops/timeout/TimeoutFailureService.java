package io.github.xianspiderman.iotops.timeout;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TimeoutFailureService {
    private final TimeoutFailureMapper mapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long workOrderId, Long executionId, String reason) {
        String message = reason == null ? "Unknown inspection failure" : reason;
        mapper.upsertFailure(workOrderId, executionId,
                message.length() <= 1000 ? message : message.substring(0, 1000), LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolve(Long id, Long operatorId) {
        mapper.resolve(id, operatorId, LocalDateTime.now(clock));
    }
}
