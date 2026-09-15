package io.github.xianspiderman.iotops.alarm;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AlarmTransactionService {
    private final AlarmMapper alarmMapper;
    private final AlarmEventRecordMapper recordMapper;
    private final DeviceMapper deviceMapper;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public Alarm process(AlarmEvent event, String payload, AlarmEventRecord existingRecord) {
        Device device = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery().eq(Device::getSn, event.deviceSn()));
        if (device == null) {
            throw new IllegalArgumentException("Device SN does not exist: " + event.deviceSn());
        }

        Alarm alarm = new Alarm();
        alarm.setEventId(event.eventId());
        alarm.setDeviceId(device.getId());
        alarm.setEventType(event.eventType());
        alarm.setSeverity(event.severity());
        alarm.setOccurredAt(event.occurredAt());
        alarm.setPayload(payload);
        alarm.setStatus("OPEN");
        alarmMapper.insert(alarm);

        if ("DEVICE_OFFLINE".equals(event.eventType())) {
            Device update = new Device();
            update.setId(device.getId());
            update.setOnlineStatus("OFFLINE");
            update.setLastCommunicationTime(event.occurredAt());
            deviceMapper.updateById(update);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (existingRecord == null) {
            AlarmEventRecord record = new AlarmEventRecord();
            record.setEventId(event.eventId());
            record.setRawPayload(payload);
            record.setProcessStatus("PROCESSED");
            record.setAlarmId(alarm.getId());
            record.setAttemptCount(1);
            record.setProcessedAt(now);
            recordMapper.insert(record);
        } else if (recordMapper.markProcessed(existingRecord.getId(), alarm.getId(), now) != 1) {
            throw new IllegalStateException("Alarm event result changed during processing");
        }
        return alarm;
    }
}
