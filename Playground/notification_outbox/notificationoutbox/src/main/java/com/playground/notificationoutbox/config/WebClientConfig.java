package com.playground.notificationoutbox.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient notificationWebClient(
            @Value("${notification.http.base-url}") String baseUrl,
            @Value("${notification.http.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${notification.http.response-timeout-ms}") int responseTimeoutMs,
            @Value("${notification.http.read-timeout-ms}") int readTimeoutMs,
            @Value("${notification.http.write-timeout-ms}") int writeTimeoutMs,
            @Value("${notification.http.max-in-memory-size}") String maxInMemory
    ) {
        ConnectionProvider provider = ConnectionProvider.builder("notification-con-pool")
                .maxConnections(200)
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS));
                });

        int bytes = (int) (Double.parseDouble(maxInMemory.replace("MB", "")));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(bytes))
                        .build())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
