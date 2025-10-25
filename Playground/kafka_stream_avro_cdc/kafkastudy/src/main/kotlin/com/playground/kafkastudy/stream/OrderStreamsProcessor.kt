package com.playground.kafkastudy.stream

import com.playground.kafkastudy.model.*
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.state.WindowStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.kafka.support.serializer.JsonSerde
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration

@Component
class OrderStreamsProcessor(
    @Value("\${kafka.topics.orders}") private val ordersTopic: String,
    @Value("\${kafka.topics.high-value-orders}") private val highValueOrdersTopic: String,
    @Value("\${kafka.topics.fraud-alerts}") private val fraudAlertsTopic: String
) {

    private val log = LoggerFactory.getLogger(OrderStreamsProcessor::class.java)

    private val orderEventSerde = createJsonSerde<OrderEvent>()
    private val fraudAlertSerde = createJsonSerde<FraudAlert>()
    private val windowedOrderCountSerde = createJsonSerde<WindowedOrderCount>()
    private val windowedSalesDataSerde = createJsonSerde<WindowedSalesData>()

    private inline fun <reified T> createJsonSerde() : JsonSerde<T> {
        return JsonSerde<T>().apply {
            configure(
                mapOf(
                    "spring.json.trusted.packages" to "com.playground.kafkastudy.model",
                    "spring.json.add.type.headers" to false,
                    "spring.json.value.default.type" to T::class.java.name
                ), false
            )
        }
    }

    @Bean
    fun orderProcessingTopology(builder: StreamsBuilder): Topology {
        val orderStream : KStream<String, OrderEvent> = builder.stream(ordersTopic, Consumed.with(Serdes.String(), orderEventSerde))
        return builder.build()
    }

    private fun highValueStream(orderStream: KStream<String, OrderEvent>) {
        val highValueStream = orderStream.filter { _, orderEvent ->
            log.info("Filter price")
            orderEvent.price >= BigDecimal.valueOf(1000)
        }

        highValueStream.to(highValueOrdersTopic, Produced.with(Serdes.String(), orderEventSerde))
    }

    private fun fraudStream(orderStream: KStream<String, OrderEvent>) {
        val fraudStream = orderStream.filter { _, orderEvent ->
            log.info("Filter price")
            orderEvent.price >= BigDecimal.valueOf(5000)
        }.mapValues { orderEvent ->
            log.info("Create FraudAlert")
            val severity = when {
                orderEvent.price >= BigDecimal.valueOf(10000) -> FraudSeverity.CRITICAL
                orderEvent.price >= BigDecimal.valueOf(5000) -> FraudSeverity.HIGH
                orderEvent.quantity > 100 -> FraudSeverity.MEDIUM
                else -> FraudSeverity.LOW
            }

            FraudAlert(
                orderId = orderEvent.orderId,
                customerId = orderEvent.customerId,
                reason = "reason",
                severity = severity,
            )
        }

        fraudStream.to(fraudAlertsTopic, Produced.with(Serdes.String(), fraudAlertSerde))
    }

    private fun orderCountStatsStream(orderStream: KStream<String, OrderEvent>) {
        // Input KStream 에 대해 10 초 간격으로 나눠서 그룹화
        orderStream
            .groupByKey(Grouped.with(Serdes.String(), orderEventSerde))
            .windowedBy(TimeWindows.of(Duration.ofSeconds(10)))
            .aggregate(
                { WindowedOrderCount() },
                { _, _, aggregate -> aggregate.increment() }
            )
    }
}
