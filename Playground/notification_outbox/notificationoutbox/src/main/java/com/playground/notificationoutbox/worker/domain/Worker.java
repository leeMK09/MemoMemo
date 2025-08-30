package com.playground.notificationoutbox.worker.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "workers")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Worker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", unique = true, nullable = false)
    private String phoneNumber;

    public Worker(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
