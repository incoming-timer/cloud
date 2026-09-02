rootProject.name = "incoming-timer-server"

include(
    "app-api",
    "app-worker",
    "app-realtime",
    "domain",
    "application",
    "infrastructure-db",
    "infrastructure-kafka",
    "infrastructure-redis",
    "support-test",
)
