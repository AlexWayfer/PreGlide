import java.util.Properties
import com.android.build.api.variant.ApplicationVariant
import dev.detekt.gradle.Detekt
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// The public checkout contains only free sources and must not create an empty premium edition.
val premiumSources = file("src/premium").isDirectory

android {
    namespace = "name.alexwayfer.preglide"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "name.alexwayfer.preglide"
        buildConfigField("boolean", "LOCAL_TRACING", "false")
        minSdk = 30
        targetSdk = 37
        // One counter for both editions: they share the application ID and install over each other, and
        // Android refuses a lower code. Every release of either edition raises it; each edition names its own version.
        versionCode = 9

        // Phones only, 64-bit and 32-bit ARM: some still run 32-bit Android. The x86 emulator is not needed yet.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Two editions, as CustomTV has: premium carries features of its own (BuildConfig.PREMIUM, src/premium). They
    // share the application ID, so one installs over the other and keeps what the keyboard learned.
    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            versionName = "1.1.1"
            buildConfigField("boolean", "PREMIUM", "false")
        }
        if (premiumSources) {
            create("premium") {
                dimension = "tier"
                isDefault = true
                versionName = "1.1.0"
                buildConfigField("boolean", "PREMIUM", "true")
            }
        }
    }

    // The release key stays outside git: keystore.properties names the keystore and its passwords.
    // Without it a release build is left unsigned, so a debug-signed APK cannot be handed out by mistake.
    val releaseKey = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    signingConfigs {
        releaseKey.getProperty("storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            // Debug and release builds carry different keys, so they install side by side instead of replacing each other.
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "LOCAL_TRACING", "true")
        }
        release {
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Keep release optimization and the installed debug app's identity, signing key, and data.
        create("optimizedDebug") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isDebuggable = false
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "LOCAL_TRACING", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric reads the app's resources and merged manifest.
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into the JDK's internals, which Java 17 and newer close (robolectric.org/getting-started).
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }
    // Android 13+ lists the languages of the values-* folders in the app's system language screen.
    androidResources {
        generateLocaleConfig = true
        // LiteRT maps a prediction model straight from the APK, which takes it stored as it is.
        noCompress += "tflite"
    }
    lint {
        // Chromebooks are not a target, so their x86_64 ABI is left out on purpose.
        disable += "ChromeOsAbiSupport"
    }
}

kotlin {
    compilerOptions {
        extraWarnings.set(true)
    }
}

// An APK to hand out is named for its edition, as the two share one id and the file alone tells them apart:
// PreGlide-free-1.1.0.apk. The optimized debug APK has its own name.
fun nameApkForItsEdition(variant: ApplicationVariant, suffix: String = "") {
    variant.outputs.forEach { output ->
        output.outputFileName.set(output.versionName.map { "PreGlide-${variant.flavorName}-$it$suffix.apk" })
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { nameApkForItsEdition(it) }
    onVariants(selector().withBuildType("optimizedDebug")) { nameApkForItsEdition(it, "-optimized-debug") }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    // Runs the next-word prediction model (model-trainer) on the phone's CPU.
    implementation(libs.litert)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    // Material You's palettes from one color, as Android builds them from the wallpaper: the keyboard's own colors.
    // The core only: MaterialKolor's Compose part would pull in an alpha of Material 3.
    implementation(libs.material.color.utilities)
    // Premium syncs what the keyboard learned through the user's Google Drive: the sign-in, and Block Store, which
    // keeps the key of the synced files on the device and in its backup.
    if (premiumSources) {
        "premiumImplementation"(libs.play.services.auth)
        "premiumImplementation"(libs.play.services.blockstore)
        // Google's QR scanner runs the camera in Play services, without an app camera permission.
        "premiumImplementation"(libs.play.services.code.scanner)
    }
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // Local HTTP servers exercise the update clients in both editions and the premium Drive client.
    testImplementation(libs.mockwebserver)
    // Android's own org.json answers only on a device; the problem journal's tests read and write JSON.
    testImplementation(libs.json)
    // Android and Compose on the JVM, so tests can touch the keys with a finger on a virtual clock.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Compose's tests wait on Espresso, whose version they bring (3.5) fails on Android 17's input manager.
    testImplementation(libs.androidx.test.espresso.core)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
}

// BuildConfig is generated Java. Kotlin compiles against its source, while detekt's type analysis needs
// the compiled class; its usual Kotlin-only dependency does not produce that class on a fresh checkout.
tasks.withType<Detekt>().configureEach {
    val variant = name.removePrefix("detekt")
    val javaTask = "compile${variant}JavaWithJavac"
    if (javaTask in tasks.names) {
        val javaCompile = tasks.named<JavaCompile>(javaTask)
        val kotlinCompile = tasks.named<KotlinJvmCompile>("compile${variant}Kotlin")
        val kotlinClasses = kotlinCompile.flatMap { it.destinationDirectory }
        dependsOn(javaCompile)
        // Explicit files replace the plugin's classpath convention: keep the complete Kotlin classpath too.
        classpath.from(kotlinCompile.map { it.libraries }, kotlinClasses,
            javaCompile.flatMap { it.destinationDirectory })
        friendPaths.from(kotlinCompile.map { it.friendPaths }, kotlinClasses)
    }
}

kover {
    reports {
        verify {
            rule {
                // Below 100% only for the lambdas inside composables, which unit tests cannot reach.
                minBound(85)
            }
        }
        filters {
            excludes {
                // Composables and Android entry points are left out: their tests would need the whole app or a
                // device. Keep rules out of them, so they stay thin. Touch handling counts: Robolectric runs it.
                annotatedBy("androidx.compose.runtime.Composable")
                classes(
                    "*.PreGlideInputMethodService",
                    "*.PreGlideSpellCheckerService*",
                    "*.SettingsActivity*",
                    "*.KeyboardStatus*",
                    // LiteRT's native library runs only on a device.
                    "*.LiteRtModel*",
                    // The Android Keystore behind premium's Mecena login, and its wiring to the app. Their rules are
                    // in MecenaResponses, MecenaDevice, MecenaHttp and PremiumAccess, which have tests.
                    "*.premium.EncryptedTextFile*",
                    "*.premium.MecenaSetupKt*",
                    "*.premium.EncryptedDevices*",
                    "*.PreGlideApplication*",
                    // The system's colors and contrast, and the wallpaper's colors, come from Android. Their rules are
                    // in Palettes, PaletteSchemes, SchemeRoles and WallpaperSeeds, which have tests.
                    "*.ui.theme.ThemeKt",
                    "*.ui.theme.WallpaperWatch*",
                    // The problem reports' Android side: the crash handler, files, notices, the share sheet.
                    // Their rules are in DiagnosticEvents, DiagnosticReport and the log files, which have tests.
                    "*.diagnostics.Diagnostics",
                    "*.diagnostics.Diagnostics$*",
                    "*.diagnostics.DiagnosticJournal",
                    "*.diagnostics.DiagnosticReportActivity*",
                    "*.diagnostics.DiagnosticNotificationKt",
                    "*.diagnostics.DiagnosticDeviceKt",
                    "*.diagnostics.AppLog",
                    "*ComposableSingletons*",
                    "*.R",
                    "*.R$*",
                    "*.BuildConfig",
                )
            }
        }
    }
}
