plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

application {
    mainClass.set("io.junshi.cli.MainKt")
}

// Run from the repository root so `--chat fixtures/...` works as documented.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.layout.projectDirectory.asFile
}
