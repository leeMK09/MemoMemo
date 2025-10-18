package com.playground.transaction.tccpoint.controller.dto;

import com.playground.transaction.tccpoint.application.dto.PointReserveCancelCommand;
import com.playground.transaction.tccpoint.application.dto.PointReserveConfirmCommand;

public record PointReserveCancelRequest(
        String requestId
) {

    public PointReserveCancelCommand toCommand() {
        return new PointReserveCancelCommand(requestId);
    }
}
