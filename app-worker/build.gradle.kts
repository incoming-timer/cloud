plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":infrastructure-db"))
    implementation(project(":infrastructure-kafka"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
