package com.playground.transaction.order.infrastructure;

import com.playground.transaction.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
