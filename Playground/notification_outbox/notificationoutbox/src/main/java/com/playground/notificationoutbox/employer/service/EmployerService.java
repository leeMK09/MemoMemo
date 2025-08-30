package com.playground.notificationoutbox.employer.service;

import com.playground.notificationoutbox.employer.domain.Employer;
import com.playground.notificationoutbox.employer.repository.EmployerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmployerService {
    private final EmployerRepository employerRepository;

    public void create() {
        employerRepository.save(
                new Employer("123456789")
        );
    }
}
