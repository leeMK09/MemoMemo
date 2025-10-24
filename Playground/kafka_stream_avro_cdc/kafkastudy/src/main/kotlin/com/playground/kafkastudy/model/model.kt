package com.playground.kafkastudy.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderEvent(
    val orderId: String,
    val customerId: String,
    val quantity: Int,
    val price: BigDecimal,
    val eventType: String = "ORDER_CREATED",
    val status: String = "PENDING",
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class CreateOrderRequest(
    val customerId: String,
    val quantity: Int,
    val price: BigDecimal,
)

// ------ stream

data class OrderCountComparisonStats(
    val currentPeriod: PeriodStats,
    val previousPeriod: PeriodStats,
    val changeCount: Long,
    val changePercentage: Double,
    val isIncreasing: Boolean,
)

data class PeriodStats(
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
    val orderCount: Long
)

data class StatsResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class WindowedOrderCount(
    val count: Long = 0L
) {
    fun increment(): WindowedOrderCount = WindowedOrderCount(count + 1)
}

data class WindowedSalesData(
    val totalSales: BigDecimal = BigDecimal.ZERO,
    val orderCount: Long = 0L
) {
    fun add(orderValue: BigDecimal): WindowedSalesData = WindowedSalesData(
        totalSales = totalSales.add(orderValue),
        orderCount = orderCount + 1
    )
}

data class FraudAlert(
    val orderId: String,
    val customerId: String,
    val reason: String,
    val severity: FraudSeverity,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime = LocalDateTime.now()
)

enum class FraudSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
