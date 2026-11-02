rootProject.name = "vice-city-app"

pluginManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2") // Maven Central
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2") // Maven Central
    }
}

include(":shared")
include(":server")
include(":webApp")
