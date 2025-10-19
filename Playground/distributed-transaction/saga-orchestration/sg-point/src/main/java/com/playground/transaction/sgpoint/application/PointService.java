package com.playground.transaction.sgpoint.application;

import com.playground.transaction.sgpoint.application.dto.PointUseCancelCommand;
import com.playground.transaction.sgpoint.application.dto.PointUseCommand;
import com.playground.transaction.sgpoint.domain.Point;
import com.playground.transaction.sgpoint.domain.PointTransactionHistory;
import com.playground.transaction.sgpoint.domain.TransactionType;
import com.playground.transaction.sgpoint.infrastructure.PointRepository;
import com.playground.transaction.sgpoint.infrastructure.PointTransactionHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private final PointRepository pointRepository;
    private final PointTransactionHistoryRepository pointTransactionHistoryRepository;

    public PointService(PointRepository pointRepository, PointTransactionHistoryRepository pointTransactionHistoryRepository) {
        this.pointRepository = pointRepository;
        this.pointTransactionHistoryRepository = pointTransactionHistoryRepository;
    }

    @Transactional
    public void use(PointUseCommand command) {
        PointTransactionHistory useHistory = pointTransactionHistoryRepository.findByRequestIdAndTransactionType(
                command.requestId(),
                TransactionType.USE
        );

        if (useHistory != null) {
            return;
        }

        Point point = pointRepository.findByUserId(command.userId());

        if (point == null) {
            throw new RuntimeException("포인트가 존재하지 않습니다");
        }

        point.use(command.amount());
        pointTransactionHistoryRepository.save(
                new PointTransactionHistory(
                        command.requestId(),
                        point.getId(),
                        command.amount(),
                        TransactionType.USE
                )
        );
    }

    @Transactional
    public void cancel(PointUseCancelCommand command) {
        PointTransactionHistory useHistory = pointTransactionHistoryRepository.findByRequestIdAndTransactionType(
                command.requestId(),
                TransactionType.USE
        );

        if (useHistory == null) {
            return;
        }

        PointTransactionHistory cancelHistory = pointTransactionHistoryRepository.findByRequestIdAndTransactionType(
                command.requestId(),
                TransactionType.CANCEL
        );

        if (cancelHistory != null) {
            return;
        }

        Point point = pointRepository.findById(useHistory.getPointId()).orElseThrow();

        point.cancel(useHistory.getAmount());
        pointTransactionHistoryRepository.save(
                new PointTransactionHistory(
                        command.requestId(),
                        point.getId(),
                        useHistory.getAmount(),
                        TransactionType.CANCEL
                )
        );
    }
}
