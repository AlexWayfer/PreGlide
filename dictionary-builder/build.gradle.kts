// Builds the word lists the keyboard suggests from: counts words in FineWeb's web text and writes the most
// frequent ones into the app's assets. A desktop tool, not part of the app; scripts\build-dictionary.ps1 runs it.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kotlin {
    compilerOptions {
        extraWarnings.set(true)
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.duckdb.jdbc)
    testImplementation(libs.junit)
}

// DuckDB loads its native library, which newer JDKs allow without a warning only when asked to.
val nativeAccess = "--enable-native-access=ALL-UNNAMED"

application {
    mainClass.set("name.alexwayfer.preglide.dictionary.MainKt")
    // The summary prints Cyrillic words, which the Windows console code page turns into question marks.
    applicationDefaultJvmArgs = listOf(nativeAccess, "-Dstdout.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    // Paths in the tool are relative to the project root: its cache and the app's assets.
    workingDir = rootDir
}

// The prediction model's training data, from the same texts; scripts\build-training-data.ps1 runs it.
tasks.register<JavaExec>("trainingData") {
    description = "Builds the prediction model's vocabulary and token files into dictionary-builder/cache/training."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("name.alexwayfer.preglide.dictionary.TrainingDataMain")
    jvmArgs(application.applicationDefaultJvmArgs)
    workingDir = rootDir
}

// The emoji panel's emoji and names, from Unicode's and CLDR's data; scripts\build-emoji.ps1 runs it.
tasks.register<JavaExec>("emoji") {
    description = "Builds the emoji panel's files into app/src/main/assets/emoji."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("name.alexwayfer.preglide.dictionary.EmojiMain")
    jvmArgs(application.applicationDefaultJvmArgs)
    workingDir = rootDir
}

tasks.test {
    jvmArgs(nativeAccess)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
}

kover {
    reports {
        verify {
            rule {
                // Every step runs on the JVM, the download against a local server, so tests reach all of it.
                minBound(95)
            }
        }
    }
}
