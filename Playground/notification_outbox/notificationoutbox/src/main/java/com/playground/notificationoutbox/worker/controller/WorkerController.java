package com.playground.notificationoutbox.worker.controller;

import com.playground.notificationoutbox.worker.domain.Worker;
import com.playground.notificationoutbox.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workers")
@RequiredArgsConstructor
public class WorkerController {
    private final WorkerService workerService;

    @PostMapping
    public ResponseEntity<String> createWorker() {
        workerService.create();
        return ResponseEntity.ok("Worker created");
    }
}
