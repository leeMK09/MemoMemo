package com.playground.kafkastudy.streamtest

/*
import com.playground.kafkastudy.model.OrderEvent
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.kafka.support.serializer.JsonSerde
import org.springframework.stereotype.Component

@Component
class OrderStreamsProcessor(
    @Value("\${kafka.topics.orders}") private val ordersTopic: String,
    @Value("\${kafka.topics.high-value-orders}") private val highValueOrdersTopic: String,
    @Value("\${kafka.topics.fraud-alerts}") private val fraudAlertsTopic: String
) {

    private val log = LoggerFactory.getLogger(OrderStreamsProcessor::class.java)

    private val orderEventSerde = createJsonSerde<OrderEvent>()

    private inline fun <reified T> createJsonSerde(): JsonSerde<T> {
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
        val orderStream: KStream<String, OrderEvent> =
            builder.stream(ordersTopic, Consumed.with(Serdes.String(), orderEventSerde))

        // 기존의 KStream 의 Key, Value 를 변환해서 새로운 KStream 을 생성
        // 입력 레코드 하나 당 출력 레코드 하나가 생성 -> map 과 비슷
        val transformedStream1: KStream<String, String> = orderStream.map { key, value ->
            KeyValue(key.toUpperCase(), "User: $value")
        }
        // 값만 변환
        val transformedStream2: KStream<String, String> = orderStream.mapValues { value ->

        }

        // KStream(Input) -> KStream(Output)
        // KStream(Input) -> KGroupedStream(Output)
        // KGroupedStream(Input) -> KTable(Output)
    }
}
*/
