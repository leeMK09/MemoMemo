package com.playground.kafkastudy.controller

import com.playground.kafkastudy.basic.OrderEventPublisher
import com.playground.kafkastudy.model.CreateOrderRequest
import com.playground.kafkastudy.model.OrderEvent
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api")
class Controller(
    private val orderEventPublisher: OrderEventPublisher,
) {

    @PostMapping
    fun createOrder(@RequestBody request : CreateOrderRequest) : ResponseEntity<String> {
        val orderEvent = OrderEvent(
            orderId = UUID.randomUUID().toString(),
            customerId = request.customerId,
            quantity = request.quantity,
            price = request.price,
        )

        orderEventPublisher.publishOrderEvent(orderEvent)
        return ResponseEntity.ok("Order created")
    }
}
