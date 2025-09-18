package com.playground.eda.client.shipping;

import com.playground.eda.client.PaymentApproved;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@Slf4j
public class ShippingFunctions {

    @Bean
    public Consumer<PaymentApproved> paymentApproved() {
        return event -> log.info("Shipping Event Consume: {}", event);
    }
}
