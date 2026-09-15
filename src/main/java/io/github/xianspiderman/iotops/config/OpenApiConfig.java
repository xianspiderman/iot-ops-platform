package io.github.xianspiderman.iotops.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI iotOpsOpenApi() {
        String securityScheme = "X-Token";
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(securityScheme,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name(securityScheme)))
                .addSecurityItem(new SecurityRequirement().addList(securityScheme))
                .info(new Info()
                        .title("IoT Ops Platform API")
                        .version("1.1.0")
                        .description("设备交付与运维管理接口；登录后使用右上角 Authorize 填入 X-Token。"));
    }
}
