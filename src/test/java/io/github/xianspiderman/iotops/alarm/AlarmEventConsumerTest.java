package io.github.xianspiderman.iotops.alarm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlarmEventConsumerTest {
    private final AlarmEventProcessor processor = mock(AlarmEventProcessor.class);
    private final AlarmEventConsumer consumer = new AlarmEventConsumer(processor);

    @Test
    void acknowledgesARecordedBusinessBadMessage() {
        when(processor.processRaw("bad")).thenReturn(AlarmProcessOutcome.BUSINESS_BAD_MESSAGE);

        assertThatCode(() -> consumer.onMessage("bad")).doesNotThrowAnyException();
        verify(processor).processRaw("bad");
    }

    @Test
    void propagatesSystemFailureSoRocketMqCanRetry() {
        when(processor.processRaw("retry")).thenThrow(new IllegalStateException("storage unavailable"));

        assertThatThrownBy(() -> consumer.onMessage("retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("storage unavailable");
    }
}
