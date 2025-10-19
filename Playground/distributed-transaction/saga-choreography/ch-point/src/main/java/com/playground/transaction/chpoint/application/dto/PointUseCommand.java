package com.playground.transaction.chpoint.application.dto;

public record PointUseCommand(
        String requestId,
        Long userId,
        Long amount
) {
}
