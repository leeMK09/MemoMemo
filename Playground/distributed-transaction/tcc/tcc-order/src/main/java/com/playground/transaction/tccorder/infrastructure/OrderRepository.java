package com.playground.transaction.tccorder.infrastructure;

import com.playground.transaction.tccorder.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
