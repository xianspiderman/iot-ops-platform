package io.github.xianspiderman.iotops.alarm;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmEventProcessor {
    private static final Set<String> EVENT_TYPES = Set.of("HIGH_TEMPERATURE", "LOW_BATTERY", "DEVICE_OFFLINE");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final ObjectMapper objectMapper;
    private final AlarmEventRecordMapper recordMapper;
    private final DeviceMapper deviceMapper;
    private final AlarmTransactionService transactionService;
    private final AlarmRecordService recordService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public AlarmProcessOutcome processRaw(String rawMessage) {
        ParsedMessage parsed = parse(rawMessage);
        if (parsed.error() != null) {
            recordService.recordBusinessFailure(parsed.eventId(), parsed.json(), parsed.error());
            meterRegistry.counter("iot.alarm.process", "outcome", "business_bad_message").increment();
            log.warn("alarm_message_rejected eventId={} reason={}", parsed.eventId(), parsed.error());
            return AlarmProcessOutcome.BUSINESS_BAD_MESSAGE;
        }
        return processValidated(parsed.event(), parsed.json(), false);
    }

    public void confirm(Long recordId, String correctedPayload, Long operatorId) {
        AlarmEventRecord record = requireRecord(recordId);
        if ("PROCESSED".equals(record.getProcessStatus())) {
            throw new BusinessException("ALARM_EVENT_ALREADY_PROCESSED", "The event was already processed");
        }
        ParsedMessage parsed = parse(correctedPayload);
        if (parsed.error() != null) {
            throw new BusinessException("INVALID_CORRECTION", parsed.error());
        }
        if (!record.getEventId().equals(parsed.event().eventId())) {
            throw new BusinessException("EVENT_ID_IMMUTABLE", "Corrected payload must keep the original eventId");
        }
        if (recordMapper.confirm(recordId, parsed.json(), operatorId, LocalDateTime.now(clock)) != 1) {
            throw new BusinessException("ALARM_EVENT_STATE_CHANGED", "The event state changed; refresh and retry");
        }
    }

    public AlarmProcessOutcome reprocess(Long recordId) {
        AlarmEventRecord record = requireRecord(recordId);
        if ("PROCESSED".equals(record.getProcessStatus())) {
            return AlarmProcessOutcome.DUPLICATE;
        }
        String payload = record.getCorrectedPayload() == null ? record.getRawPayload() : record.getCorrectedPayload();
        ParsedMessage parsed = parse(payload);
        if (parsed.error() != null) {
            recordService.recordBusinessFailure(record.getEventId(), parsed.json(), parsed.error());
            throw new BusinessException("ALARM_EVENT_STILL_INVALID", parsed.error());
        }
        if (!record.getEventId().equals(parsed.event().eventId())) {
            throw new BusinessException("EVENT_ID_IMMUTABLE", "Replay payload eventId does not match its failure record");
        }
        return processValidated(parsed.event(), parsed.json(), true);
    }

    private AlarmProcessOutcome processValidated(AlarmEvent event, String json, boolean manual) {
        String validationError = validate(event);
        if (validationError == null && deviceMapper.selectCount(
                Wrappers.<Device>lambdaQuery().eq(Device::getSn, event.deviceSn())) == 0) {
            validationError = "Device SN does not exist: " + event.deviceSn();
        }
        if (validationError != null) {
            recordService.recordBusinessFailure(event.eventId(), json, validationError);
            meterRegistry.counter("iot.alarm.process", "outcome", "business_bad_message").increment();
            if (manual) {
                throw new BusinessException("ALARM_EVENT_STILL_INVALID", validationError);
            }
            return AlarmProcessOutcome.BUSINESS_BAD_MESSAGE;
        }

        AlarmEventRecord existing = findByEventId(event.eventId());
        if (existing != null && "PROCESSED".equals(existing.getProcessStatus())) {
            log.info("alarm_message_duplicate eventId={} alarmId={}", event.eventId(), existing.getAlarmId());
            meterRegistry.counter("iot.alarm.process", "outcome", "duplicate").increment();
            return AlarmProcessOutcome.DUPLICATE;
        }
        if (!manual && existing != null && ("BUSINESS_BAD_MESSAGE".equals(existing.getProcessStatus())
                || "CONFIRMED".equals(existing.getProcessStatus()))) {
            return AlarmProcessOutcome.BUSINESS_BAD_MESSAGE;
        }

        try {
            Alarm alarm = transactionService.process(event, json, existing);
            meterRegistry.counter("iot.alarm.process", "outcome", "processed").increment();
            log.info("alarm_message_processed eventId={} alarmId={} deviceSn={}",
                    event.eventId(), alarm.getId(), event.deviceSn());
            return AlarmProcessOutcome.PROCESSED;
        } catch (DataIntegrityViolationException exception) {
            AlarmEventRecord concurrent = findByEventId(event.eventId());
            if (concurrent != null && "PROCESSED".equals(concurrent.getProcessStatus())) {
                log.info("alarm_message_concurrent_duplicate eventId={} alarmId={}",
                        event.eventId(), concurrent.getAlarmId());
                meterRegistry.counter("iot.alarm.process", "outcome", "duplicate").increment();
                return AlarmProcessOutcome.DUPLICATE;
            }
            recordService.recordSystemFailure(event.eventId(), json, rootMessage(exception));
            meterRegistry.counter("iot.alarm.process", "outcome", "system_failure").increment();
            throw exception;
        } catch (RuntimeException exception) {
            recordService.recordSystemFailure(event.eventId(), json, rootMessage(exception));
            meterRegistry.counter("iot.alarm.process", "outcome", "system_failure").increment();
            throw exception;
        }
    }

    private ParsedMessage parse(String rawMessage) {
        String raw = rawMessage == null ? "" : rawMessage.trim();
        try {
            JsonNode tree = objectMapper.readTree(raw);
            String json = objectMapper.writeValueAsString(tree);
            String eventId = tree.path("eventId").asText("").trim();
            if (eventId.isEmpty()) {
                eventId = invalidFingerprint(raw);
                return new ParsedMessage(null, eventId, json, "eventId is required");
            }
            AlarmEvent event = objectMapper.treeToValue(tree, AlarmEvent.class);
            return new ParsedMessage(event, eventId, json, null);
        } catch (JsonProcessingException exception) {
            String eventId = invalidFingerprint(raw);
            try {
                return new ParsedMessage(null, eventId,
                        objectMapper.writeValueAsString(java.util.Map.of("unparsed", raw)), "Payload is not valid JSON");
            } catch (JsonProcessingException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    private String validate(AlarmEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            return "eventId is required";
        }
        if (event.eventId().length() > 96) {
            return "eventId must not exceed 96 characters";
        }
        if (event.deviceSn() == null || event.deviceSn().isBlank()) {
            return "deviceSn is required";
        }
        if (!EVENT_TYPES.contains(event.eventType())) {
            return "Unsupported eventType";
        }
        if (!SEVERITIES.contains(event.severity())) {
            return "Unsupported severity";
        }
        if (event.occurredAt() == null) {
            return "occurredAt is required";
        }
        return null;
    }

    private AlarmEventRecord requireRecord(Long id) {
        AlarmEventRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("ALARM_EVENT_NOT_FOUND", "Alarm event record does not exist");
        }
        return record;
    }

    private AlarmEventRecord findByEventId(String eventId) {
        return recordMapper.selectOne(Wrappers.<AlarmEventRecord>lambdaQuery()
                .eq(AlarmEventRecord::getEventId, eventId));
    }

    private String invalidFingerprint(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return "INVALID-" + HexFormat.of().formatHex(digest, 0, 20);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private record ParsedMessage(AlarmEvent event, String eventId, String json, String error) {
    }
}
