package com.playground.notificationoutbox.notification.controller;

import com.playground.notificationoutbox.notification.controller.dto.NotificationRequest;
import com.playground.notificationoutbox.notification.service.NotificationService;
import com.playground.notificationoutbox.notification.service.dto.NotificationCreation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<String> send(@RequestBody NotificationRequest request) {
        NotificationCreation creation = new NotificationCreation(request.employerId(), request.workerId());
        notificationService.create(creation);
        return ResponseEntity.ok("notification sent");
    }
}
