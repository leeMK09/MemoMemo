package com.playground.transaction.sgorder.infrastructure;

import com.playground.transaction.sgorder.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
