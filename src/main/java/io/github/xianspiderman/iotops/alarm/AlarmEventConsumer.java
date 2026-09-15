package io.github.xianspiderman.iotops.alarm;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "iot-ops.alarm.rocketmq-enabled", havingValue = "true")
@RocketMQMessageListener(topic = "${iot-ops.alarm.topic}", consumerGroup = "${rocketmq.consumer.group}")
public class AlarmEventConsumer implements RocketMQListener<String> {
    private final AlarmEventProcessor processor;

    @Override
    public void onMessage(String message) {
        processor.processRaw(message);
    }
}
