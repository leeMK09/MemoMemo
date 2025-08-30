package com.playground.notificationoutbox.worker.repository;

import com.playground.notificationoutbox.worker.domain.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
}
