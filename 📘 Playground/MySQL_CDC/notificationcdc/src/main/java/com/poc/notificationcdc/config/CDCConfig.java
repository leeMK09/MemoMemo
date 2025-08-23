package com.poc.notificationcdc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CDCProperties.class)
public class CDCConfig {
}
