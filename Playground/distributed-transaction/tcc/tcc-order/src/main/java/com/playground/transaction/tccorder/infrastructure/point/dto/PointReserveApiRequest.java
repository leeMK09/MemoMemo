package com.playground.transaction.tccorder.infrastructure.point.dto;

public record PointReserveApiRequest(
        String requestId,
        Long userId,
        Long reserveAmount
) {
}
