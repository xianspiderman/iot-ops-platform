package io.github.xianspiderman.iotops.alarm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlarmRecordService {
    private final AlarmEventRecordMapper recordMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBusinessFailure(String eventId, String json, String reason) {
        recordMapper.upsertFailure(eventId, json, "BUSINESS_BAD_MESSAGE", "BUSINESS", abbreviate(reason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystemFailure(String eventId, String json, String reason) {
        recordMapper.upsertFailure(eventId, json, "SYSTEM_FAILURE", "SYSTEM", abbreviate(reason));
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "Unknown processing failure";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
