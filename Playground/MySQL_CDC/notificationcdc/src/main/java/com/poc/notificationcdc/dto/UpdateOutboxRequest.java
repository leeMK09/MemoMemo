package com.poc.notificationcdc.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record UpdateOutboxRequest(
        long id,
        @NotEmpty Map<String, Object> payload
) {
}
