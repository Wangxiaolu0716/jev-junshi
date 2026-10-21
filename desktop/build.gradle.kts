plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("io.junshi.desktop.MainKt")
}

/**
 * One self-contained jar: `jpackage` then turns it into a Windows app image,
 * and the knowledge index travels inside it as resources.
 */
val fatJar by tasks.registering(Jar::class) {
    archiveFileName.set("jev-junshi-desktop.jar")
    manifest {
        attributes["Main-Class"] = "io.junshi.desktop.MainKt"
        attributes["Implementation-Title"] = "jev-junshi"
        attributes["Implementation-Version"] = project.version.toString()
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
}

tasks.named("build") { dependsOn(fatJar) }
