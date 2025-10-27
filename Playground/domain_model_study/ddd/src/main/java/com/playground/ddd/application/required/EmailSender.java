package com.playground.ddd.application.required;

import com.playground.ddd.domain.Email;

public interface EmailSender {
    void send(Email email, String subject, String body);
}
