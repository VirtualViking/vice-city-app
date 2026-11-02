import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Modelos y constantes que comparten el servidor y la web
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    // Las pruebas de shared se ejecutan en la JVM; se desactivan las de navegador (evitan descargar Karma)
    js { browser { testTask { enabled = false } } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser { testTask { enabled = false } } }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
