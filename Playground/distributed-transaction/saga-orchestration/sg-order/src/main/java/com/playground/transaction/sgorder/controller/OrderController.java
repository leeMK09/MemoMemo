package com.playground.transaction.sgorder.controller;

import com.playground.transaction.sgorder.application.OrderCoordinator;
import com.playground.transaction.sgorder.application.OrderService;
import com.playground.transaction.sgorder.application.RedisLockService;
import com.playground.transaction.sgorder.application.dto.CreateOrderResult;
import com.playground.transaction.sgorder.controller.dto.CreateOrderRequest;
import com.playground.transaction.sgorder.controller.dto.CreateOrderResponse;
import com.playground.transaction.sgorder.controller.dto.PlaceOrderRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final OrderCoordinator orderCoordinator;
    private final RedisLockService redisLockService;

    public OrderController(OrderService orderService, OrderCoordinator orderCoordinator, RedisLockService redisLockService) {
        this.orderService = orderService;
        this.orderCoordinator = orderCoordinator;
        this.redisLockService = redisLockService;
    }

    @PostMapping("/order")
    public CreateOrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        CreateOrderResult result = orderService.createOrder(request.toCommand());

        return new CreateOrderResponse(result.orderId());
    }

    @PostMapping("/order/place")
    public void placeOrder(@RequestBody PlaceOrderRequest request) {
        String key = "order:" + request.orderId();

        boolean acquired = redisLockService.tryLock(key, request.orderId().toString());

        if (!acquired) {
            throw new RuntimeException("락 획득에 실패하였습니다");
        }

        try {
            orderCoordinator.placeOrder(request.toCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }
}
