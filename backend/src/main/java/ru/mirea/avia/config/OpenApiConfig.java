package ru.mirea.avia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Авиабронирование — REST API")
                        .version("1.0.0")
                        .description("Backend информационной системы бронирования авиабилетов (КР №3). "
                                + "Войдите через POST /api/auth/login и вставьте accessToken в Authorize."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
