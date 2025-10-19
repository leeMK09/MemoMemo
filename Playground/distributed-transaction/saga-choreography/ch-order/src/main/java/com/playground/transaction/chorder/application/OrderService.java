package com.playground.transaction.chorder.application;

import com.playground.transaction.chorder.application.dto.PlaceOrderCommand;
import com.playground.transaction.chorder.domain.Order;
import com.playground.transaction.chorder.domain.OrderItem;
import com.playground.transaction.chorder.infrastructure.OrderItemRepository;
import com.playground.transaction.chorder.infrastructure.OrderRepository;
import com.playground.transaction.chorder.infrastructure.kafka.OrderPlacedProducer;
import com.playground.transaction.chorder.infrastructure.kafka.dto.OrderPlacedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPlacedProducer orderPlacedProducer;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository, OrderPlacedProducer orderPlacedProducer) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderPlacedProducer = orderPlacedProducer;
    }

    @Transactional
    public void placeOrder(PlaceOrderCommand command) {
        Order order = orderRepository.findById(command.orderId()).orElseThrow();
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());

        order.request();
        orderRepository.save(order);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderPlacedProducer.send(
                        new OrderPlacedEvent(
                                command.orderId(),
                                orderItems.stream()
                                        .map(orderItem -> new OrderPlacedEvent.ProductInfo(orderItem.getProductId(), orderItem.getQuantity()))
                                        .toList()
                        )
                );
            }
        });
    }
}
