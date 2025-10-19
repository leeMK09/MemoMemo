package com.playground.transaction.sgproduct.infrastructure;

import com.playground.transaction.sgproduct.domain.ProductTransactionHistory;
import com.playground.transaction.sgproduct.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductTransactionHistoryRepository extends JpaRepository<ProductTransactionHistory, Long> {
    List<ProductTransactionHistory> findAllByRequestIdAndTransactionType(
            String requestId,
            TransactionType transactionType
    );
}
