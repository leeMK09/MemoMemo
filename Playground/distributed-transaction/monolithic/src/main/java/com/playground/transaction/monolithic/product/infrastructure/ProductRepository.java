package com.playground.transaction.monolithic.product.infrastructure;

import com.playground.transaction.monolithic.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
