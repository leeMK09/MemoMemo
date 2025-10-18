package com.playground.transaction.tccpoint.controller.dto;

import com.playground.transaction.tccpoint.application.dto.PointReserveCommand;
import com.playground.transaction.tccpoint.application.dto.PointReserveConfirmCommand;

public record PointReserveConfirmRequest(
        String requestId
) {

    public PointReserveConfirmCommand toCommand() {
        return new PointReserveConfirmCommand(requestId);
    }
}
