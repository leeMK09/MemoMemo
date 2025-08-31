package com.playground.notificationoutbox.worker.service;

import com.playground.notificationoutbox.worker.service.dto.WorkerResult;

public interface WorkerReader {
    WorkerResult getById(Long workerId);
}
