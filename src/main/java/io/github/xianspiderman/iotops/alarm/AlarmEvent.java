package io.github.xianspiderman.iotops.alarm;

import java.time.LocalDateTime;
import java.util.Map;

public record AlarmEvent(String eventId, String deviceSn, String eventType, String severity,
                         LocalDateTime occurredAt, Map<String, Object> data) {
}
