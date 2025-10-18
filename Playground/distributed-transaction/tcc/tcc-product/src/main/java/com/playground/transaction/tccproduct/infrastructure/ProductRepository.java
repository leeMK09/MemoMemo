package com.playground.transaction.tccproduct.infrastructure;

import com.playground.transaction.tccproduct.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
