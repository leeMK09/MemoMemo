package com.playground.kafkastudy.basic

import com.playground.kafkastudy.model.OrderEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, OrderEvent>,
    @Value("\${kafka.topics.orders}")
    private val ordersTopic: String
) {
    private val log = LoggerFactory.getLogger(OrderEventPublisher::class.java)

    fun publishOrderEvent(orderEvent: OrderEvent) {
        try {
            // 키가 없다면 라운드 로빈으로 동작, 현재는 orderID 기준으로 순차적 처리를 위해 orderID 로 키 설정
            kafkaTemplate.send(ordersTopic, orderEvent.orderId, orderEvent)
                .whenComplete { _, ex ->
                    if (ex != null) {
                        log.error("Error during send order event: {}", ex.message)
                    } else {
                        log.info("Successfully sent order event: {}", orderEvent)
                    }
                }
        } catch (ex : Exception) {
            log.error("Error publishing order event: {}", ex.message, ex)
        }
    }
}
