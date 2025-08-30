package com.playground.notificationoutbox.worker.service;

import com.playground.notificationoutbox.worker.domain.Worker;
import com.playground.notificationoutbox.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final WorkerRepository workerRepository;

    public void create() {
        workerRepository.save(
                new Worker("987654321")
        );
    }
}
