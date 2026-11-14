import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Keyboard logic without Android: layouts now, then the dictionary, predictions and the glide decoder.
// Plain JVM, so its tests run without an emulator or the Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // A bytecode target alone still exposes newer JDK APIs missing on older Android versions.
        freeCompilerArgs.add("-Xjdk-release=11")
        extraWarnings.set(true)
    }
}

dependencies {
    testImplementation(libs.junit)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
}

tasks.named<Detekt>("detektTest") {
    // The compatibility fixture is Java so it can model APIs hidden from the Kotlin compiler.
    classpath.from(tasks.named<JavaCompile>("compileTestJava").flatMap { it.destinationDirectory })
}

kover {
    reports {
        verify {
            rule {
                // Engine logic runs without Android, so every rule in it can and should have a test.
                minBound(95)
            }
        }
    }
}
