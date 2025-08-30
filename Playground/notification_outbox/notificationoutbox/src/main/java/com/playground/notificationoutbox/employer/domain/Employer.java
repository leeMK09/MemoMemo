package com.playground.notificationoutbox.employer.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "employers")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Employer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", unique = true, nullable = false)
    private String phoneNumber;

    public Employer(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
