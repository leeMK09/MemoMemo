package com.playground.transaction.chproduct.application;

import com.playground.transaction.chproduct.application.dto.ProductBuyCancelCommand;
import com.playground.transaction.chproduct.application.dto.ProductBuyCancelResult;
import com.playground.transaction.chproduct.application.dto.ProductBuyCommand;
import com.playground.transaction.chproduct.application.dto.ProductBuyResult;
import com.playground.transaction.chproduct.domain.Product;
import com.playground.transaction.chproduct.infrastructure.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductBuyResult buy(ProductBuyCommand command) {
        Long totalPrice = 0L;
        for (ProductBuyCommand.ProductInfo productInfo : command.productInfos()) {
            Product product = productRepository.findById(productInfo.productId()).orElseThrow();

            product.buy(productInfo.quantity());
            Long price = product.calculatePrice(productInfo.quantity());
            totalPrice += price;
        }

        return new ProductBuyResult(totalPrice);
    }

    @Transactional
    public ProductBuyCancelResult cancel(ProductBuyCancelCommand command) {
        return new ProductBuyCancelResult(0L);
    }
}
