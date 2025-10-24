package com.playground.kafkastudy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KafkastudyApplication

fun main(args: Array<String>) {
    runApplication<KafkastudyApplication>(*args)
}
