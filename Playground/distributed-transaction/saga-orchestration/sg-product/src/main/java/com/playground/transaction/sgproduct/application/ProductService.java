package com.playground.transaction.sgproduct.application;

import com.playground.transaction.sgproduct.application.dto.ProductBuyCancelCommand;
import com.playground.transaction.sgproduct.application.dto.ProductBuyCancelResult;
import com.playground.transaction.sgproduct.application.dto.ProductBuyCommand;
import com.playground.transaction.sgproduct.application.dto.ProductBuyResult;
import com.playground.transaction.sgproduct.domain.Product;
import com.playground.transaction.sgproduct.domain.ProductTransactionHistory;
import com.playground.transaction.sgproduct.domain.TransactionType;
import com.playground.transaction.sgproduct.infrastructure.ProductRepository;
import com.playground.transaction.sgproduct.infrastructure.ProductTransactionHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductTransactionHistoryRepository productTransactionHistoryRepository;

    public ProductService(ProductRepository productRepository, ProductTransactionHistoryRepository productTransactionHistoryRepository) {
        this.productRepository = productRepository;
        this.productTransactionHistoryRepository = productTransactionHistoryRepository;
    }

    @Transactional
    public ProductBuyResult buy(ProductBuyCommand command) {
        List<ProductTransactionHistory> histories = productTransactionHistoryRepository.findAllByRequestIdAndTransactionType(
                command.requestId(),
                TransactionType.PURCHASE
        );

        if (!histories.isEmpty()) {
            long totalPrice = histories.stream()
                    .mapToLong(ProductTransactionHistory::getPrice)
                    .sum();

            return new ProductBuyResult(totalPrice);
        }

        Long totalPrice = 0L;
        for (ProductBuyCommand.ProductInfo productInfo : command.productInfos()) {
            Product product = productRepository.findById(productInfo.productId()).orElseThrow();

            product.buy(productInfo.quantity());
            Long price = product.calculatePrice(productInfo.quantity());
            totalPrice += price;
            productTransactionHistoryRepository.save(
                    new ProductTransactionHistory(
                            command.requestId(),
                            productInfo.productId(),
                            productInfo.quantity(),
                            price,
                            TransactionType.PURCHASE
                    )
            );
        }

        return new ProductBuyResult(totalPrice);
    }

    @Transactional
    public ProductBuyCancelResult cancel(ProductBuyCancelCommand command) {
        List<ProductTransactionHistory> histories = productTransactionHistoryRepository.findAllByRequestIdAndTransactionType(
                command.requestId(),
                TransactionType.PURCHASE
        );

        if (histories.isEmpty()) {
            return new ProductBuyCancelResult(0L);
        }

        List<ProductTransactionHistory> cancelHistories = productTransactionHistoryRepository.findAllByRequestIdAndTransactionType(
                command.requestId(),
                TransactionType.CANCEL
        );

        if (!cancelHistories.isEmpty()) {
            long totalPrice = cancelHistories.stream()
                    .mapToLong(ProductTransactionHistory::getPrice)
                    .sum();
            return new ProductBuyCancelResult(totalPrice);
        }

        Long totalPrice = 0L;
        for (ProductTransactionHistory history : histories) {
            Product product = productRepository.findById(history.getProductId()).orElseThrow();

            product.cancel(history.getQuantity());
            totalPrice += history.getPrice();
            productTransactionHistoryRepository.save(
                    new ProductTransactionHistory(
                            command.requestId(),
                            history.getProductId(),
                            history.getQuantity(),
                            history.getPrice(),
                            TransactionType.CANCEL
                    )
            );
        }

        return new ProductBuyCancelResult(totalPrice);
    }
}
