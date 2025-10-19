package com.playground.transaction.sgpoint.infrastructure;

import com.playground.transaction.sgpoint.domain.PointTransactionHistory;
import com.playground.transaction.sgpoint.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointTransactionHistoryRepository extends JpaRepository<PointTransactionHistory, Integer> {
    PointTransactionHistory findByRequestIdAndTransactionType(String requestId, TransactionType transactionType);
}
