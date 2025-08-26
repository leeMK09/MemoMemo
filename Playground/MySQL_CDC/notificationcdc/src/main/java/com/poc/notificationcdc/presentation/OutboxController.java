package com.poc.notificationcdc.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.notificationcdc.dto.CreateOutboxRequest;
import com.poc.notificationcdc.dto.DeleteOutboxRequest;
import com.poc.notificationcdc.dto.UpdateOutboxRequest;
import com.poc.notificationcdc.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxController {

    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public String insertTest(@RequestBody CreateOutboxRequest request) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(request);
        outboxEventService.insert(json);
        return "[Insert] success";
    }

    @PutMapping
    public String updateTest(@RequestBody UpdateOutboxRequest request) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(request.payload());
        outboxEventService.update(request.id(), json);
        return "[Update] success";
    }

    @DeleteMapping
    public String deleteTest(@RequestBody DeleteOutboxRequest request) {
        outboxEventService.delete(request.id());
        return "[Delete] success";
    }
}
