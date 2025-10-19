package com.playground.transaction.sgorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class SgOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SgOrderApplication.class, args);
    }

}
