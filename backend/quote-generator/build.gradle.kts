val clickhouseVersion = "0.7.2"
val jedisVersion = "5.2.0"
val logbackVersion = "1.5.18"

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    application
}

group = "com.highloadinvest"
version = "0.1.0"

application {
    mainClass.set("com.highloadinvest.quotegen.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.clickhouse:clickhouse-jdbc:$clickhouseVersion")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.4.4")
    implementation("redis.clients:jedis:$jedisVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveFileName.set("quote-generator-all.jar")
    mergeServiceFiles()
}

kotlin {
    jvmToolchain(17)
}
