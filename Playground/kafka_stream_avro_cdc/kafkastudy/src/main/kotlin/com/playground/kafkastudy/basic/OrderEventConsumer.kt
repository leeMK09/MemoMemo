package com.playground.kafkastudy.basic

import com.playground.kafkastudy.model.OrderEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer {
    private val log = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    // KafkaMessageListenerContainer 생성 그 후 컨슈머가 카프카 클러스터와 연결하면 지정한 토픽을 기준으로 메시지를 주기적으로 폴링하면서 메시지를 받음
    // group ID, 메시지를 스레드 기준으로 분산 처리할 수 있음, 여러 컨슈머 인스턴스를 논리적으로 묶어주는 식별자 역할, 같은 그룹 ID 가 있는 카프카 컨슈머가 있다면 한 곳에서만 전송
    @KafkaListener(
        topics = ["\${kafka.topics.orders}"],
        groupId = "order-processing-group",
        concurrency = "3", // 일반적으로는 토픽에 있는 파티션의 개수만큼 지정 -> 과도한 스레드 풀은 놀고있는 스레드를 만들 수 있음
        containerFactory = "orderEventKafkaListenerContainerFactory"
    )
    fun processOrder(
        @Payload orderEvent: OrderEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: String,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        log.info("Received event: $orderEvent, partition: $partition, offset: $offset")

        try {
            processLogic()
            log.info("info event: $orderEvent, partition: $partition, offset: $offset")
        } catch (ex : Exception) {
            log.error(ex.message, ex)
        }
    }

    private fun processLogic() {
        Thread.sleep(100)
    }
}

@Component
class OrderAnalyticsConsumer {
    private val log = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    // KafkaMessageListenerContainer 생성 그 후 컨슈머가 카프카 클러스터와 연결하면 지정한 토픽을 기준으로 메시지를 주기적으로 폴링하면서 메시지를 받음
    // group ID, 메시지를 스레드 기준으로 분산 처리할 수 있음, 여러 컨슈머 인스턴스를 논리적으로 묶어주는 식별자 역할, 같은 그룹 ID 가 있는 카프카 컨슈머가 있다면 한 곳에서만 전송
    @KafkaListener(
        topics = ["\${kafka.topics.orders}"],
        groupId = "order-analytics-group",
        concurrency = "2", // 일반적으로는 토픽에 있는 파티션의 개수만큼 지정 -> 과도한 스레드 풀은 놀고있는 스레드를 만들 수 있음
        containerFactory = "orderEventKafkaListenerContainerFactory"
    )
    fun collectAnalytics(
        @Payload orderEvent: OrderEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: String,
    ) {
        log.info("Received event: $orderEvent, partition: $partition")

        try {
            updateStatistics(orderEvent)
        } catch (ex : Exception) {
            log.error(ex.message, ex)
        }
    }

    private fun updateStatistics(orderEvent: OrderEvent) {
        log.info("Updated event: $orderEvent")
    }
}

@Component
class OrderNotificationConsumer {
    private val log = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    // KafkaMessageListenerContainer 생성 그 후 컨슈머가 카프카 클러스터와 연결하면 지정한 토픽을 기준으로 메시지를 주기적으로 폴링하면서 메시지를 받음
    // group ID, 메시지를 스레드 기준으로 분산 처리할 수 있음, 여러 컨슈머 인스턴스를 논리적으로 묶어주는 식별자 역할, 같은 그룹 ID 가 있는 카프카 컨슈머가 있다면 한 곳에서만 전송
    @KafkaListener(
        topics = ["\${kafka.topics.orders}"],
        groupId = "order-notification-group",
        concurrency = "1", // 일반적으로는 토픽에 있는 파티션의 개수만큼 지정 -> 과도한 스레드 풀은 놀고있는 스레드를 만들 수 있음
        containerFactory = "orderEventKafkaListenerContainerFactory"
    )
    fun sendNotification(
        @Payload orderEvent: OrderEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: String,
    ) {
        log.info("Notification event: $orderEvent, partition: $partition")

        try {
            processLogic()
        } catch (ex : Exception) {
            log.error(ex.message, ex)
        }
    }

    private fun processLogic() {
        log.info("Notification!")
    }
}
