package com.playground.elasticsearchproduct.product;

import com.playground.elasticsearchproduct.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

}
