package com.incomingtimer.realtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.incomingtimer"])
class RealtimeApplication

fun main(args: Array<String>) {
    runApplication<RealtimeApplication>(*args)
}
