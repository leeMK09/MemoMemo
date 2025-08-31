package com.playground.notificationoutbox.employer.service;

import com.playground.notificationoutbox.employer.domain.Employer;
import com.playground.notificationoutbox.employer.repository.EmployerRepository;
import com.playground.notificationoutbox.employer.service.dto.EmployerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployerService implements EmployerReader {
    private final EmployerRepository employerRepository;

    public void create() {
        employerRepository.save(
                new Employer("123456789")
        );
    }

    @Override
    public EmployerResult getById(Long employerId) {
        Employer employer = employerRepository.findById(employerId).orElseThrow(() -> {
            log.error("employer 를 찾을 수 없습니다. employerId = {}", employerId);
            return new RuntimeException("employer 를 찾을 수 없습니다.");
        });
        return new EmployerResult(employer.getPhoneNumber());
    }
}
