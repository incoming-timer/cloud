plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":infrastructure-kafka"))
    implementation(project(":infrastructure-redis"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
