import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.20"
    application
}

group = "fi.kapsi.kosmik"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:2.1.2")
    implementation("io.ktor:ktor-server-netty:2.1.2")

    implementation("io.ktor:ktor-client-core:2.1.2")
    // CIO (coroutine I/O) based backend for ktor-client
    implementation("io.ktor:ktor-client-cio:2.1.2")

    implementation("io.arrow-kt:arrow-fx-coroutines:1.1.2")
    implementation("io.arrow-kt:arrow-fx-stm:1.1.2")

    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.h2database:h2:2.1.214")
    implementation("org.flywaydb:flyway-core:9.5.1")
    implementation("com.github.seratch:kotliquery:1.9.0")

    implementation("io.ktor:ktor-server-html-builder:2.1.2")

    implementation("at.favre.lib:bcrypt:0.9.0")

    implementation("ch.qos.logback:logback-classic:1.4.4")
    implementation("org.slf4j:slf4j-api:2.0.3")

    implementation("com.typesafe:config:1.4.2")
    implementation("com.google.code.gson:gson:2.10")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("kotlinbook.MainKt")
}
