package com.playground.notificationoutbox.employer.repository;

import com.playground.notificationoutbox.employer.domain.Employer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerRepository extends JpaRepository<Employer, Long> {
}
