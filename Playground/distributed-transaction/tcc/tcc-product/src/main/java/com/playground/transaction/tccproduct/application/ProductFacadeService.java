package com.playground.transaction.tccproduct.application;

import com.playground.transaction.tccproduct.application.dto.ProductReserveCancelCommand;
import com.playground.transaction.tccproduct.application.dto.ProductReserveCommand;
import com.playground.transaction.tccproduct.application.dto.ProductReserveConfirmCommand;
import com.playground.transaction.tccproduct.application.dto.ProductReserveResult;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class ProductFacadeService {

    private final ProductService productService;

    public ProductFacadeService(ProductService productService) {
        this.productService = productService;
    }

    public ProductReserveResult tryReserve(ProductReserveCommand command) {
        int tryCount = 0;

        while (tryCount < 3) {
            try {
                return productService.tryReserve(command);
            } catch (ObjectOptimisticLockingFailureException e) {
                tryCount++;
            }
        }

        throw new RuntimeException("예약에 실패하였습니다");
    }

    public void confirmReserve(ProductReserveConfirmCommand command) {
        int tryCount = 0;

        while (tryCount < 3) {
            try {
                productService.confirmReserve(command);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                tryCount++;
            }
        }

        throw new RuntimeException("확정에 실패하였습니다");
    }

    public void cancelReserve(ProductReserveCancelCommand command) {
        int tryCount = 0;

        while (tryCount < 3) {
            try {
                productService.cancelReserve(command);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                tryCount++;
            }
        }

        throw new RuntimeException("취소에 실패하였습니다");
    }
}
