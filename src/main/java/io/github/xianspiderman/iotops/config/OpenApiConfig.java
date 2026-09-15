package io.github.xianspiderman.iotops.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI iotOpsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("IoT Ops Platform API")
                .version("0.1.0")
                .description("Device delivery and operations management APIs"));
    }
}

