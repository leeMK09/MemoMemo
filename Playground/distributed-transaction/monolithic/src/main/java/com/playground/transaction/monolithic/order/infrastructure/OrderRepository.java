package com.playground.transaction.monolithic.order.infrastructure;

import com.playground.transaction.monolithic.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
