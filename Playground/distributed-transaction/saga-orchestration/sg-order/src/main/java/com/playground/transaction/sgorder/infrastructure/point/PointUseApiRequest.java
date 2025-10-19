package com.playground.transaction.sgorder.infrastructure.point;

public record PointUseApiRequest(
        String requestId,
        Long userId,
        Long amount
) {
}
