package com.playground.transaction.tccorder.controller;

import com.playground.transaction.tccorder.application.OrderCoordinator;
import com.playground.transaction.tccorder.application.OrderService;
import com.playground.transaction.tccorder.application.RedisLockService;
import com.playground.transaction.tccorder.application.dto.CreateOrderResult;
import com.playground.transaction.tccorder.controller.dto.CreateOrderRequest;
import com.playground.transaction.tccorder.controller.dto.CreateOrderResponse;
import com.playground.transaction.tccorder.controller.dto.PlaceOrderRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final RedisLockService redisLockService;
    private final OrderCoordinator orderCoordinator;

    public OrderController(OrderService orderService, RedisLockService redisLockService, OrderCoordinator orderCoordinator) {
        this.orderService = orderService;
        this.redisLockService = redisLockService;
        this.orderCoordinator = orderCoordinator;
    }

    @PostMapping("/order")
    public CreateOrderResponse createOrder(
            @RequestBody CreateOrderRequest request
    ) {
        CreateOrderResult result = orderService.createOrder(request.toCreateOrderCommand());

        return new CreateOrderResponse(result.orderId());
    }

    @PostMapping("/order/place")
    public void placeOrder(
            @RequestBody PlaceOrderRequest request
    ) {
        String key = "order:" + request.orderId();
        boolean acquiredLock = redisLockService.tryLock(key, request.orderId().toString());

        if (!acquiredLock) {
            throw new RuntimeException("락획득에 실패하였습니다");
        }

        try {
            orderCoordinator.placeOrder(request.toCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }
}
