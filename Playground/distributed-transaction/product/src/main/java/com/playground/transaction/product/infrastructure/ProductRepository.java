package com.playground.transaction.product.infrastructure;

import com.playground.transaction.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
