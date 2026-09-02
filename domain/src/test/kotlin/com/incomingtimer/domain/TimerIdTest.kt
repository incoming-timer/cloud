package com.incomingtimer.domain

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TimerIdTest {
    @Test
    fun `retains UUID value`() {
        val value = UUID.randomUUID()

        assertEquals(value, TimerId(value).value)
    }
}
