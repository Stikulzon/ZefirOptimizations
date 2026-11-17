pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.kikugie.dev/releases") {
            name = "KikuGie Releases"
        }
        mavenCentral()
        gradlePluginPortal()
    }

    val spotless_version: String by settings
    plugins {
        id("com.diffplug.spotless") version spotless_version
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.10"
    id("dev.kikugie.loom-back-compat") version "0.4.3"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"
    create(rootProject) {
        versions("1.21.1", "26.2")
        vcsVersion = "26.2"
    }
}

rootProject.name = "zefiroptimizations"
