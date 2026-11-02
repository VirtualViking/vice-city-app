plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "vicecity"
version = "0.1.0"

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
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}
