package com.prosto.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI analyticsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Analytics Pivot API")
                        .description("""
                                API для бизнес-аналитики: сводные таблицы на больших объёмах данных.
                                
                                Возможности:
                                - Загрузка CSV-датасетов (до 100M записей, 200 атрибутов)
                                - Построение сводных таблиц с агрегацией на стороне PostgreSQL
                                - AI-ассистент для генерации pivot-конфигураций
                                - Экспорт результатов в CSV
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("PROSTO Team")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local dev")
                ));
    }
}
