package com.playground.eda.client.order;

import com.playground.eda.client.OrderCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final StreamBridge streamBridge;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrderRequest request) {
        long orderId = ThreadLocalRandom.current().nextLong(1, 1_000_000);
        OrderCreated event = new OrderCreated(orderId, request.userId(), request.amount());
        log.info("Created order: {}", event);
        streamBridge.send("orderCreated-out-0", event);
        return ResponseEntity.ok(event);
    }
}

record CreateOrderRequest(Long userId, int amount) {}
