package com.playground.transaction.sgpoint.controller.dto;

import com.playground.transaction.sgpoint.application.dto.PointUseCancelCommand;
import com.playground.transaction.sgpoint.application.dto.PointUseCommand;

public record PointUseCancelRequest(
        String requestId
) {

    public PointUseCancelCommand toCommand() {
        return new PointUseCancelCommand(requestId);
    }
}
