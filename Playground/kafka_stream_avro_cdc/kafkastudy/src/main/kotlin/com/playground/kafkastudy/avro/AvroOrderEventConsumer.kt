package com.playground.kafkastudy.avro

import org.apache.avro.generic.GenericRecord
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

@Component
class AvroOrderEventConsumer {

    private fun extractOrderDataFromAvro(record: GenericRecord): AvroOrderDate {
        return AvroOrderDate(
            orderId = record.get("orderId").toString(),
            customerId = record.get("customerId").toString(),
            quantity = record.get("quantity").toString().toInt(),
            price = convertBytesToPrice(record.get("price") as ByteBuffer)
        )
    }

    private fun convertBytesToPrice(byteBuffer: ByteBuffer): BigDecimal {
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        val bigInt = BigInteger(bytes)
        return BigDecimal(bigInt, 2)
    }

    data class AvroOrderDate(
        val orderId: String,
        val customerId: String,
        val quantity: Int,
        val price: BigDecimal,
    )
}
