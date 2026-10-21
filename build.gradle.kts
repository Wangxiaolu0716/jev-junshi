import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("com.android.application") version "8.7.3" apply false
    kotlin("android") version "2.0.21" apply false
}

subprojects {
    // The Android module has its own toolchain and plugin set.
    if (path == ":android-app") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        google()
        mavenCentral()
    }

    // Target 17 without requiring a local JDK 17 toolchain: the build runs on
    // whatever JDK Gradle uses and emits 17-compatible bytecode.
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    dependencies {
        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
