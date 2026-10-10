package com.incomingtimer.db

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {
    @Test
    fun `migrates the synchronization schema`() {
        val flyway =
            Flyway
                .configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .load()

        val migration = flyway.migrate()

        assertEquals(1, migration.migrationsExecuted)

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.metaData.getTables(null, "public", null, arrayOf("TABLE")).use { tables ->
                val tableNames =
                    buildSet {
                        while (tables.next()) {
                            add(tables.getString("TABLE_NAME"))
                        }
                    }

                assertEquals(
                    setOf("flyway_schema_history", "timers", "timer_operations", "change_log", "outbox_events"),
                    tableNames,
                )
            }
        }
    }

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
