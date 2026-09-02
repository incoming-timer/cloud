dependencies {
    implementation(project(":application"))
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation(kotlin("test"))
}
