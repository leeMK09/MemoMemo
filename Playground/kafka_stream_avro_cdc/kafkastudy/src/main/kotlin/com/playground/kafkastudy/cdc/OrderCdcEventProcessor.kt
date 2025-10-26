package com.playground.kafkastudy.cdc

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class OrderCdcEventProcessor(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(OrderCdcEventProcessor::class.java)

    @KafkaListener(
        topics = ["dbserver1.public.orders"],
        groupId = "real-order-cdc-processor",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun processCdcEvent(@Payload cdcMessage: String) {
        try {
            val cdcEvent = objectMapper.readTree(cdcMessage)
            log.info(cdcEvent.toString())
            // convert to entity
        } catch (e: Exception) {
            log.error(e.message, e)
        }
    }
}
