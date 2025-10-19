package com.playground.transaction.chorder.infrastructure;

import com.playground.transaction.chorder.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
