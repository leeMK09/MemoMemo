package com.playground.transaction.chorder.controller;

import com.playground.transaction.chorder.application.OrderService;
import com.playground.transaction.chorder.application.RedisLockService;
import com.playground.transaction.chorder.controller.dto.PlaceOrderRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final RedisLockService redisLockService;

    public OrderController(OrderService orderService, RedisLockService redisLockService) {
        this.orderService = orderService;
        this.redisLockService = redisLockService;
    }

    @PostMapping("/order/place")
    public void placeOrder(@RequestBody PlaceOrderRequest request) {
        String key = "order:" + request.orderId();

        boolean acquired = redisLockService.tryLock(key, request.orderId().toString());

        if (!acquired) {
            throw new RuntimeException("락 획득에 실패하였습니다");
        }

        try {
            orderService.placeOrder(request.toCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }
}
