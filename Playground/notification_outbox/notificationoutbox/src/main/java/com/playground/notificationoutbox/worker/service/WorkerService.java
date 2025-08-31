package com.playground.notificationoutbox.worker.service;

import com.playground.notificationoutbox.worker.domain.Worker;
import com.playground.notificationoutbox.worker.repository.WorkerRepository;
import com.playground.notificationoutbox.worker.service.dto.WorkerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService implements WorkerReader {
    private final WorkerRepository workerRepository;

    public void create() {
        workerRepository.save(
                new Worker("987654321")
        );
    }

    @Override
    public WorkerResult getById(Long workerId) {
        Worker worker = workerRepository.findById(workerId).orElseThrow(() -> {
            log.error("worker 를 찾을 수 없습니다. workerId = {}", workerId);
            return new RuntimeException("worker 를 찾을 수 없습니다.");
        });
        return new WorkerResult(worker.getPhoneNumber());
    }
}
