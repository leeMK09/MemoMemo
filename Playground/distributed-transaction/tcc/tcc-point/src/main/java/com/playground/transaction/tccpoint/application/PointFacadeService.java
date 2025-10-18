package com.playground.transaction.tccpoint.application;

import com.playground.transaction.tccpoint.application.dto.PointReserveCancelCommand;
import com.playground.transaction.tccpoint.application.dto.PointReserveCommand;
import com.playground.transaction.tccpoint.application.dto.PointReserveConfirmCommand;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class PointFacadeService {

    private final PointService pointService;

    public PointFacadeService(PointService pointService) {
        this.pointService = pointService;
    }

    public void tryReserve(PointReserveCommand command) {
        int tryCount = 0;

        while (tryCount < 3) {
            try {
                pointService.tryReserve(command);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                tryCount++;
            }
        }

        throw new RuntimeException("예약에 실패하였습니다");
    }

    public void confirmReserve(PointReserveConfirmCommand command) {
        int tryCount = 0;

        while (tryCount < 3) {
            try {
                pointService.confirmReserve(command);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                tryCount++;
            }
        }

        throw new RuntimeException("확정에 실패하였습니다");
    }

    public void cancelReserve(PointReserveCancelCommand command) {
        int tryCount = 0;

        while (tryCount < 3) {
            try {
                pointService.cancelReserve(command);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                tryCount++;
            }
        }

        throw new RuntimeException("취소에 실패하였습니다");
    }
}
