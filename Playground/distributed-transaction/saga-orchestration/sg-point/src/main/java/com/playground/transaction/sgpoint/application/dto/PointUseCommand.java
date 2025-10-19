package com.playground.transaction.sgpoint.application.dto;

public record PointUseCommand(
        String requestId,
        Long userId,
        Long amount
) {
}
