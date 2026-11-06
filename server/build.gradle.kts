plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "vicecity"
version = "0.3.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("vicecity.servidor.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.bcrypt)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.zxing.core)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.logback)

    // Ktor 3.6.1 trae Netty 4.2.17 y java-jwt 4.6.0, con vulnerabilidades conocidas.
    // Se fuerzan las versiones corregidas mientras Ktor no las actualice.
    implementation(platform(libs.netty.bom))
    constraints {
        implementation(libs.java.jwt) { because("CVE-2026-85473, corregida en 4.6.2") }
    }

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    // Las pruebas de integración usan PostgreSQL real si TEST_DATABASE_URL está definida
    environment("TEST_DATABASE_URL", System.getenv("TEST_DATABASE_URL") ?: "")
    // Muestra cada prueba en la consola (y en el log del CI) con su resultado
    testLogging {
        events("passed", "skipped", "failed")
    }
}