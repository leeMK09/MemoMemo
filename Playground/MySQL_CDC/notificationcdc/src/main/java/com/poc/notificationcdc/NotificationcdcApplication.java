package com.poc.notificationcdc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotificationcdcApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationcdcApplication.class, args);
    }

}
