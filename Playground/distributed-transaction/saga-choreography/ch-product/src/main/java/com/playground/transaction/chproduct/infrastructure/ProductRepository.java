package com.playground.transaction.chproduct.infrastructure;

import com.playground.transaction.chproduct.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
