package com.playground.notificationoutbox.notification.service;

import com.playground.notificationoutbox.employer.service.EmployerReader;
import com.playground.notificationoutbox.employer.service.dto.EmployerResult;
import com.playground.notificationoutbox.notification.domain.NotificationRequested;
import com.playground.notificationoutbox.notification.service.dto.NotificationCreation;
import com.playground.notificationoutbox.outbox.service.OutboxService;
import com.playground.notificationoutbox.worker.service.WorkerReader;
import com.playground.notificationoutbox.worker.service.dto.WorkerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final OutboxService outboxService;
    private final EmployerReader employerReader;
    private final WorkerReader workerReader;

    @Transactional
    public void create(NotificationCreation creation) {
        EmployerResult employer = employerReader.getById(creation.employerId());
        WorkerResult worker = workerReader.getById(creation.workerId());
        NotificationRequested notificationRequested = new NotificationRequested(
                creation.occurredAt(),
                employer.phoneNumber(),
                worker.phoneNumber()
        );
        outboxService.create(notificationRequested, creation.maxAttempts());
    }
}
