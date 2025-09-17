package com.playground.resilience4j.test.products;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerEventListener() {
        circuitBreakerRegistry.circuitBreaker("productService").getEventPublisher()
                .onStateTransition(event -> log.info("#######CircuitBreaker State Transition: {}", event)) // 상태 전환 이벤트 리스너
                .onFailureRateExceeded(event -> log.info("#######CircuitBreaker Failure Rate Exceeded: {}", event)) // 실패율 초과 이벤트 리스너
                .onCallNotPermitted(event -> log.info("#######CircuitBreaker Call Not Permitted: {}", event)) // 호출 차단 이벤트 리스너
                .onError(event -> log.info("#######CircuitBreaker Error: {}", event)); // 오류 발생 이벤트 리스너
    }


    @CircuitBreaker(name = "productService", fallbackMethod = "fallbackGetProductDetails")
    public Product getProductDetails(String productId) {
        log.info("###Fetching product details for productId: {}", productId);
        if ("111".equals(productId)) {
            log.warn("###Received empty body for productId: {}", productId);
            throw new RuntimeException("Empty response body");
        }
        return new Product(
                productId,
                "Sample Product"
        );
    }

    public Product fallbackGetProductDetails(String productId, Throwable t) {
        log.error("####Fallback triggered for productId: {} due to: {}", productId, t.getMessage());
        return new Product(
                productId,
                "Fallback Product"
        );
    }
}
