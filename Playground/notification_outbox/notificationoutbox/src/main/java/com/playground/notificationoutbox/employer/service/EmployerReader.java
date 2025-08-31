package com.playground.notificationoutbox.employer.service;

import com.playground.notificationoutbox.employer.service.dto.EmployerResult;

public interface EmployerReader {
    EmployerResult getById(Long employerId);
}
