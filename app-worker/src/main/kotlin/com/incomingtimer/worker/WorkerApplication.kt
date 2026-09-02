package com.incomingtimer.worker

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

@SpringBootApplication(scanBasePackages = ["com.incomingtimer"])
class WorkerApplication

fun main(args: Array<String>) {
    SpringApplicationBuilder(WorkerApplication::class.java)
        .web(WebApplicationType.NONE)
        .run(*args)
}
