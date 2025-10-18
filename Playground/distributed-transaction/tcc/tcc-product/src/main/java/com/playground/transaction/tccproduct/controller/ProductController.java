package com.playground.transaction.tccproduct.controller;

import com.playground.transaction.tccproduct.application.ProductFacadeService;
import com.playground.transaction.tccproduct.application.ProductService;
import com.playground.transaction.tccproduct.application.RedisLockService;
import com.playground.transaction.tccproduct.application.dto.ProductReserveCancelCommand;
import com.playground.transaction.tccproduct.application.dto.ProductReserveResult;
import com.playground.transaction.tccproduct.controller.dto.ProductReserveCancelRequest;
import com.playground.transaction.tccproduct.controller.dto.ProductReserveConfirmRequest;
import com.playground.transaction.tccproduct.controller.dto.ProductReserveRequest;
import com.playground.transaction.tccproduct.controller.dto.ProductReserveResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    private final ProductFacadeService productFacadeService;
    private final RedisLockService redisLockService;

    public ProductController(ProductFacadeService productFacadeService, RedisLockService redisLockService) {
        this.productFacadeService = productFacadeService;
        this.redisLockService = redisLockService;
    }

    @PostMapping("/product/reserve")
    public ProductReserveResponse reserve(@RequestBody ProductReserveRequest request) {
        String key = "product:" + request.requestId();
        boolean acquiredLock = redisLockService.tryLock(key, request.requestId());

        if (!acquiredLock) {
            throw new RuntimeException("락 획득에 실패하였습니다.");
        }

        try {
            ProductReserveResult result = productFacadeService.tryReserve(request.toCommand());

            return new ProductReserveResponse(result.totalPrice());
        } finally {
            redisLockService.releaseLock(key);
        }
    }

    @PostMapping("/product/confirm")
    public void confirm(@RequestBody ProductReserveConfirmRequest request) {
        String key = "product:" + request.requestId();
        boolean acquiredLock = redisLockService.tryLock(key, request.requestId());

        if (!acquiredLock) {
            throw new RuntimeException("락 획득에 실패하였습니다.");
        }

        try {
            productFacadeService.confirmReserve(request.toCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }

    @PostMapping("/product/cancel")
    public void cancel(@RequestBody ProductReserveCancelRequest request) {
        String key = "product:" + request.requestId();
        boolean acquiredLock = redisLockService.tryLock(key, request.requestId());

        if (!acquiredLock) {
            throw new RuntimeException("락 획득에 실패하였습니다.");
        }

        try {
            productFacadeService.cancelReserve(request.toCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }
}
