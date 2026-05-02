val ktorVersion = "3.1.2"
val kotlinVersion = "2.1.20"
val koinVersion = "4.1.0-Beta5"
val logbackVersion = "1.5.18"
val clickhouseVersion = "0.7.2"
val jedisVersion = "5.2.0"

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("io.ktor.plugin") version "3.1.2"
}

group = "com.highloadinvest"
version = "0.1.0"

application {
    mainClass.set("com.highloadinvest.gateway.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // (Koin removed — manual DI to avoid Ktor 2.x classloading conflict)

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // ClickHouse (HTTP API)
    implementation("org.json:json:20250107")

    // Redis
    implementation("redis.clients:jedis:$jedisVersion")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
}

ktor {
    fatJar {
        archiveFileName.set("api-gateway-all.jar")
    }
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
