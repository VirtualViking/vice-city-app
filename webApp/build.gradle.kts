import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Respaldo JavaScript para navegadores sin WasmGC (modo de compatibilidad)
    js {
        outputModuleName = "webApp"
        browser {
            testTask { enabled = false }
        }
        binaries.executable()
    }

    // Versión principal: WebAssembly
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "webApp"
        browser {
            testTask { enabled = false }
        }
        binaries.executable()
    }

    sourceSets {
        webMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
    }
}
