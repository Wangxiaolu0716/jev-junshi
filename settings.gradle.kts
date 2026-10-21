pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jev-junshi"

include(":core", ":cli", ":adapter-jarvis", ":desktop", ":android-app")
