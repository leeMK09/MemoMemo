package com.playground.transaction.sgorder.infrastructure.point;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClient;

public class PointApiClient {
    private final RestClient restClient;

    public PointApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Retryable(
            retryFor = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public void use(PointUseApiRequest request) {
        restClient.post()
                .uri("/point/use")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Retryable(
            retryFor = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public void cancel(PointUseCancelApiRequest request) {
        restClient.post()
                .uri("/point/use/cancel")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
