package com.playground.eda.client.payment;

import com.playground.eda.client.OrderCreated;
import com.playground.eda.client.PaymentApproved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFunctions {
    private final StreamBridge streamBridge;

    @Bean
    public Consumer<OrderCreated> handleOrder() {
        return event -> {
            boolean approve = event.amount() <= 10_000;
            if (approve) {
                PaymentApproved ok = new PaymentApproved(
                        event.orderId(),
                        event.userId(),
                        event.amount(),
                        UUID.randomUUID().toString()
                );
                log.info("Order approved: {}", ok);
                streamBridge.send("paymentApproved-out-0", ok);
            } else {
                log.info("Payment approved but not approved");
                // 실패 이벤트 발송
            }
        };
    }
}
