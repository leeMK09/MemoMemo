package com.playground.transaction.sgpoint.controller;

import com.playground.transaction.sgpoint.application.PointService;
import com.playground.transaction.sgpoint.application.RedisLockService;
import com.playground.transaction.sgpoint.controller.dto.PointUseCancelRequest;
import com.playground.transaction.sgpoint.controller.dto.PointUseRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PointController {
    private final PointService pointService;
    private final RedisLockService redisLockService;

    public PointController(PointService pointService, RedisLockService redisLockService) {
        this.pointService = pointService;
        this.redisLockService = redisLockService;
    }

    @PostMapping("/point/use")
    public void use(@RequestBody PointUseRequest request) {
        String key = "point:orchestration:" + request.requestId();

        boolean acquired = redisLockService.tryLock(key, request.requestId());

        if (!acquired) {
            throw new RuntimeException("락 획득에 실패하였습니다.");
        }

        try {
            pointService.use(request.toCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }

    @PostMapping("/point/cancel")
    public void cancel(@RequestBody PointUseCancelRequest request) {
        String key = "point:orchestration:" + request.requestId();

        boolean acquired = redisLockService.tryLock(key, request.requestId());

        if (!acquired) {
            throw new RuntimeException("락 획득에 실패하였습니다.");
        }

        try {
            pointService.cancel(request.toCommand());
        } finally {
            redisLockService.releaseLock(key);
        }
    }
}
