package com.playground.transaction.sgproduct.controller;

import com.playground.transaction.sgproduct.application.ProductService;
import com.playground.transaction.sgproduct.application.RedisLockService;
import com.playground.transaction.sgproduct.application.dto.ProductBuyCancelResult;
import com.playground.transaction.sgproduct.application.dto.ProductBuyResult;
import com.playground.transaction.sgproduct.controller.dto.ProductBuyCancelRequest;
import com.playground.transaction.sgproduct.controller.dto.ProductBuyCancelResponse;
import com.playground.transaction.sgproduct.controller.dto.ProductBuyRequest;
import com.playground.transaction.sgproduct.controller.dto.ProductBuyResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {
    private final ProductService productService;
    private final RedisLockService redisLockService;

    public ProductController(ProductService productService, RedisLockService redisLockService) {
        this.productService = productService;
        this.redisLockService = redisLockService;
    }

    @PostMapping("/product/buy")
    public ProductBuyResponse buy(@RequestBody ProductBuyRequest request) {
        String key = "product:orchestration:" + request.requestId();

        boolean acquired = redisLockService.tryLock(key, request.requestId());

        if (!acquired) {
            throw new RuntimeException("락 획득에 실패하였습니다");
        }

        try {
            ProductBuyResult result = productService.buy(request.toCommand());

            return new ProductBuyResponse(result.totalPrice());
        } finally {
            redisLockService.releaseLock(key);
        }
    }

    @PostMapping("/product/buy/cancel")
    public ProductBuyCancelResponse cancel(@RequestBody ProductBuyCancelRequest request) {
        String key = "product:orchestration:" + request.requestId();

        boolean acquired = redisLockService.tryLock(key, request.requestId());

        if (!acquired) {
            throw new RuntimeException("락 획득에 실패하였습니다");
        }

        try {
            ProductBuyCancelResult result = productService.cancel(request.toCommand());

            return new ProductBuyCancelResponse(result.totalPrice());
        } finally {
            redisLockService.releaseLock(key);
        }
    }
}
