package com.example.aggregationservice.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicsConfig {

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic userForecastUpdateTopic() {
        return new NewTopic("user_forecast_update", 10, (short) 1);
    }

    @Bean
    public NewTopic transactionExportTopic() {
        return new NewTopic("transaction_export_events", 1, (short) 1);
    }

    @Bean
    public NewTopic forecastReadyTopic() {
        return new NewTopic("forecast_ready", 10, (short) 1);
    }
}