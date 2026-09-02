import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0" apply false
}

allprojects {
    group = "com.incomingtimer"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
            mavenBom("org.testcontainers:testcontainers-bom:1.21.3")
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            allWarningsAsErrors.set(true)
        }
        jvmToolchain(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
