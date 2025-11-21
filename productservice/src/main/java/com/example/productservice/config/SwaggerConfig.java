package com.example.productservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI leadServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinPulse Lead Service API")
                        .description("API для управления заявками на банковские продукты")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FinPulse Team")
                                .email("support@finpulse.ru")));
    }
}