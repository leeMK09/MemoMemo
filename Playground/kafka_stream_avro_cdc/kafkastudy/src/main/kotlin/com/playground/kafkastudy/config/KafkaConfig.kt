package com.playground.kafkastudy.config

import com.playground.kafkastudy.model.OrderEvent
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    companion object {
        const val SCHEMA_REGISTRY_URL = "http://localhost:8081"
    }

    @Bean
    fun orderEventConsumerFactory(): ConsumerFactory<String, OrderEvent> {
        // ErrorHandlingDeserializer : 직렬화 + 예외처리 가능(애플리케이션 중단 방지, DLQ 같은 방어로직 추가 가능함, 데코레이터 패턴)
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS to StringDeserializer::class.java,
            ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,
            JsonDeserializer.VALUE_DEFAULT_TYPE to OrderEvent::class.java, // Strict Type 타입 강제
            JsonDeserializer.TRUSTED_PACKAGES to "*",
            JsonDeserializer.USE_TYPE_INFO_HEADERS to false // OrderEvent 로 강타입 강제를 했으니 헤더 파싱 오버헤드 제거
        )

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun orderEventKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, OrderEvent> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, OrderEvent>()
        factory.consumerFactory = orderEventConsumerFactory()
        return factory
    }

    @Bean
    fun genericConsumerFactory(): ConsumerFactory<String, Any> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS to StringDeserializer::class.java,
            ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,
            JsonDeserializer.TRUSTED_PACKAGES to "*",
            JsonDeserializer.USE_TYPE_INFO_HEADERS to true
        )

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun genericKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = genericConsumerFactory()
        return factory
    }

    @Bean
    fun cdcConsumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "real-order-cdc-processor",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest", // 계속 데이터 전체를 읽음
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true, // CDC 이벤트가 들어왔을때 바로 자동 커밋
            ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG to 1000
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun cdcKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = cdcConsumerFactory()
        return factory
    }

    @Bean
    fun avroConsumerFactory(): ConsumerFactory<String, GenericRecord> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to SCHEMA_REGISTRY_URL,
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to false,
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to true
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun avroKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, GenericRecord>()
        factory.consumerFactory = avroConsumerFactory()
        return factory
    }

    // -------- producer --------- //
    @Bean
    fun orderEventProducerFactory(): ProducerFactory<String, OrderEvent> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to true
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, OrderEvent> {
        return KafkaTemplate(orderEventProducerFactory())
    }

    @Bean
    fun avroProducerFactory(): ProducerFactory<String, GenericRecord> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to SCHEMA_REGISTRY_URL,
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to true, // 자동 스키마 추가
            ProducerConfig.ACKS_CONFIG to "1", // 브로커에게 메시지 전송이 되었는지 기다리는 옵션, 0/1/all, 0은 확인X, 1은 Leader 에게만 전송되었는지 확인, all은 복제본까지 확인
            ProducerConfig.RETRIES_CONFIG to 3, // 재시도 설정
            ProducerConfig.BATCH_SIZE_CONFIG to 16384, // 지연 버퍼 개념
            ProducerConfig.LINGER_MS_CONFIG to 10, // 지연 버퍼 개념 (시간 기준)
            ProducerConfig.COMPRESSION_TYPE_CONFIG to "snappy" // none(압축안함), lz4, gzip, zstd
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun avroKafkaTemplate(): KafkaTemplate<String, GenericRecord> {
        return KafkaTemplate(avroProducerFactory())
    }
}
