package com.playground.kafkastudy.stream

import com.playground.kafkastudy.model.OrderCountComparisonStats
import com.playground.kafkastudy.model.WindowedOrderCount
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyWindowStore
import org.slf4j.LoggerFactory
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OrderStreamsService(
    private val factory : StreamsBuilderFactoryBean
) {
    private val log = LoggerFactory.getLogger(OrderStreamsService::class.java)

    fun orderCountComparison(): OrderCountComparisonStats? {
        return try {
            val stream = factory.kafkaStreams
            if (stream == null || stream.state() != KafkaStreams.State.RUNNING) {
                return null
            }

            // local store 사용
            val store: ReadOnlyWindowStore<String, WindowedOrderCount> = stream.store(
                StoreQueryParameters.fromNameAndType("order-count-store", QueryableStoreTypes.windowStore())
            )

            // 지금이 9시면 8:55 ~ 9:00 / 8:50 ~ 8:55 두 시간 범위를 데이터를 가져와서 비교
            val now = Instant.now()
            val currentPeriodEnd = now
            val currentPeriodStart = now.minusSeconds(300)

            val prevPeriodEnd = currentPeriodStart
            val prevPeriodStart = currentPeriodStart.minusSeconds(300)

            val currentCount = countForPeriod(store, currentPeriodStart, currentPeriodEnd)
            val prevCount = countForPeriod(store, prevPeriodStart, prevPeriodEnd)

            return null
        } catch (e: Exception) {
            log.error(e.message, e)
            return null
        }
    }

    private fun countForPeriod(
        store: ReadOnlyWindowStore<String, WindowedOrderCount>,
        startTime: Instant,
        endTime: Instant,
    ): Long {
        var totalCount = 0L

        store.fetchAll(startTime, endTime).use { iter ->
            while (iter.hasNext()) {
                val entry = iter.next()
                totalCount += entry.value.count
            }
        }
        return totalCount
    }
}
