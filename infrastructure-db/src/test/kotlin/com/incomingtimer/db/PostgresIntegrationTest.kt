package com.incomingtimer.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {
    @Test
    fun `connects to PostgreSQL`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select 1").use { result ->
                    result.next()
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
