package com.poc.notificationcdc.config;

import com.poc.notificationcdc.scheduler.OutboxRowMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OutboxMapperConfig {

    @Bean
    public OutboxRowMapper outboxRowMapper(JdbcTemplate jdbcTemplate, CDCProperties cdcProperties) {
        return OutboxRowMapper.load(jdbcTemplate, cdcProperties.getSchema(), cdcProperties.getIncludeTable());
    }
}
