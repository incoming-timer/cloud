package com.incomingtimer.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.incomingtimer"])
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
