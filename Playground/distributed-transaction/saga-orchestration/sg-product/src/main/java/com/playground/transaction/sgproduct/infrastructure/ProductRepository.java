package com.playground.transaction.sgproduct.infrastructure;

import com.playground.transaction.sgproduct.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
