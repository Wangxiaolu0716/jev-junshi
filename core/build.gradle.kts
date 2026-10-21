plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

sourceSets {
    main {
        // Generated knowledge index (index.jsonl / routes.json / manifest.json)
        // is bundled as resources so a jar or an APK can retrieve offline.
        resources.srcDir("../kb")
    }
}
