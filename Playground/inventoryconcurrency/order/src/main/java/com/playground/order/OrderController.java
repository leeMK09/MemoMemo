package com.playground.order;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create")
    public void createOrder(@RequestBody CreateRequest request) {
        orderService.createOrder(request);
    }

    @PostMapping("/cancel")
    public void cancelOrder(@RequestBody DeleteRequest request) {
        orderService.cancelOrder(request.orderId());
    }

    @DeleteMapping
    public void deleteOrder(@RequestBody DeleteRequest request) {
        orderService.deleteOrder(request.orderId());
    }
}
