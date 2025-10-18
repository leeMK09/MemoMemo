package com.playground.transaction.monolithic.order.controller;

import com.playground.transaction.monolithic.order.application.OrderService;
import com.playground.transaction.monolithic.order.application.RedisLockService;
import com.playground.transaction.monolithic.order.application.dto.CreateOrderResult;
import com.playground.transaction.monolithic.order.controller.dto.CreateOrderRequest;
import com.playground.transaction.monolithic.order.controller.dto.CreateOrderResponse;
import com.playground.transaction.monolithic.order.controller.dto.PlaceOrderRequest;
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
        String key = "order:monolithic:" + request.orderId();
        boolean acquiredLock = redisLockService.tryLock(key, request.orderId().toString());

        if (!acquiredLock) {
            throw new RuntimeException("락획득에 실패하였습니다");
        }

        try {
            orderService.placeOrder(request.toPlaceOrderCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }
}
