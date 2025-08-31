package com.playground.notificationoutbox.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("notificationExecutor")
    public Executor notificationExecutor(
            @Value("${notification.async.pool.core}") int core,
            @Value("${notification.async.pool.max}") int max,
            @Value("${notification.async.pool.queue}") int queue,
            @Value("${notification.async.pool.keep-alive-seconds}") int keepAliveSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("notification-async-");
        executor.initialize();
        return executor;
    }
}
