package com.playground.transaction.sgproduct.init;

import com.playground.transaction.sgproduct.domain.Product;
import com.playground.transaction.sgproduct.infrastructure.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class TestDataCreator {

    private final ProductRepository productRepository;

    public TestDataCreator(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @PostConstruct
    public void createTestData() {
        Product product1 = new Product(100L, 100L);
        Product product2 = new Product(100L, 200L);

        productRepository.save(product1);
        productRepository.save(product2);
    }
}
