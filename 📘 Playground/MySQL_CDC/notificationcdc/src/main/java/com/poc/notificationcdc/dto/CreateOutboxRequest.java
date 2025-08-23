package com.poc.notificationcdc.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record CreateOutboxRequest(@NotEmpty Map<String, Object> payload) {
}
