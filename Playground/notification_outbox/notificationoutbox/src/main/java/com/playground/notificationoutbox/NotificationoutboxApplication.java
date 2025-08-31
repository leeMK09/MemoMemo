package com.playground.notificationoutbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotificationoutboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationoutboxApplication.class, args);
    }

}
