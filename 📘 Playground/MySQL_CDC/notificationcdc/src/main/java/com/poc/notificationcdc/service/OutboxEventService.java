package com.poc.notificationcdc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.notificationcdc.domain.OutboxEvent;
import com.poc.notificationcdc.infrastructure.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    public void insert(String json) {
        outboxEventRepository.save(OutboxEvent.builder().payload(json).build());
    }

    public void delete(long id) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(id).orElseThrow();
        outboxEventRepository.delete(outboxEvent);
    }

    public void update(long id, String json) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(id).orElseThrow();
        outboxEvent.setPayload(json);
    }
}
