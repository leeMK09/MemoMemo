package com.playground.notificationoutbox.employer.controller;

import com.playground.notificationoutbox.employer.service.EmployerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/employers")
@RequiredArgsConstructor
public class EmployerController {
    private final EmployerService employerService;

    @PostMapping
    public ResponseEntity<String> createEmployer() {
        employerService.create();
        return ResponseEntity.ok("Employer created");
    }
}
