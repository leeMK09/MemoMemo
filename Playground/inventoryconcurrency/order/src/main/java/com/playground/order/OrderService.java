package com.playground.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    private Logger log = LoggerFactory.getLogger(OrderService.class);

    public OrderService(OrderRepository orderRepository, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public void createOrder(CreateRequest request) {
        log.info("Creating order");
        orderRepository.save(new Order(request.inventoryId(), request.quantity()));

        log.info("call Inventory increase");
        inventoryClient.increase(
                new Request(
                        request.inventoryId(),
                        request.quantity()
                )
        );

        log.info("Complete Create order");
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();

        order.cancel();

        inventoryClient.decrease(
                new Request(
                    order.getInventoryId(),
                    order.getQuantity()
                )
        );
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();

        order.delete();

        inventoryClient.decrease(
                new Request(
                        order.getInventoryId(),
                        order.getQuantity()
                )
        );
    }
}
