package com.playground.kafkastudy.avro

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.Schema
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class SchemaManager(
    @Value("\${schema.registry.url:http://localhost:8081}")
    private val schemaRegistryUrl: String,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(SchemaManager::class.java)

    private var cachedSchema: Schema? = null

    private val client: SchemaRegistryClient = CachedSchemaRegistryClient(schemaRegistryUrl, 100)

    fun orderEventSchema(): Schema {
        return cachedSchema ?: loadSchemaFromRegistry().also {
            cachedSchema = it
        }
    }

    private fun loadSchemaFromRegistry(): Schema {
        return try {
            val subject = "orders-avro-value"

            val last = client.getLatestSchemaMetadata(subject)
            val schemaString = last.schema

            Schema.Parser().parse(schemaString)
        } catch (e: Exception) {
            log.error(e.message, e)
            loadSchemaFromFile("avro/order-entity.avsc")
        }
    }

    private fun loadSchemaFromFile(path: String): Schema {
        return try {
            log.info("path {}", path)
            val resource = ClassPathResource(path)
            val content = resource.inputStream.bufferedReader().use { it.readText() }
            Schema.Parser().parse(content)
        } catch (e: IOException) {
            throw IllegalStateException("Failed to load schema from $path", e)
        }
    }

    private fun registerSchemaIfNotExists(): Int? {
        // subject 안에 여러 버전의 schema 가 존재
        return try {
            val subject = "orders-avro-value"

            val existing = try {
                client.getAllVersions(subject)
            } catch (e: Exception) {
                log.error(e.message, e)
                emptyList<Int>()
            }

            if (existing.isNotEmpty()) {
                val last = client.getLatestSchemaMetadata(subject)
                return last.id
            }

            val resource = loadSchemaFromFile("avro/order-entity.avsc")
            val avroSchema = AvroSchema(resource)
            val id = client.register(subject, avroSchema)

            id
        } catch (e: Exception) {
            log.error(e.message, e)
            null
        }
    }

    override fun run(args: ApplicationArguments?) {
//        registerSchemaIfNotExists()
    }
}
