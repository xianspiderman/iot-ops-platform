package io.github.xianspiderman.iotops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {
    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}

