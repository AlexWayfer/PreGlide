import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    // dependencyUpdates lists newer versions of the dependencies and plugins; scripts\outdated.ps1 runs it.
    alias(libs.plugins.versions)
}

// A stable version is offered only stable updates; a pre-release, such as detekt's alpha, anything newer.
tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isPreRelease(candidate.version) && !isPreRelease(currentVersion) }
    gradleReleaseChannel = "current"
    // Only the classpaths that hold what the version catalog declares. Resolving all of the dozens of internal
    // configurations (Kotlin's own, lint's, android tests) is what made the check slow.
    filterConfigurations = Spec { it.name in checkedConfigurations }
}

val checkedConfigurations = setOf(
    // Premium holds all of free's libraries and its own, so premium's configurations cover both editions.
    "premiumDebugRuntimeClasspath",
    "premiumDebugUnitTestRuntimeClasspath",
    "runtimeClasspath",
    "testRuntimeClasspath",
    "detekt",
)

fun isPreRelease(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    return !stableKeyword && !Regex("^[0-9,.v-]+(-r)?$").matches(version)
}

// verify.ps1 -DetektOnly lists the changed Kotlin files in this file, one path a line: each detekt task then analyzes
// only its own of them, and resolves the types of the other files from its module's compiled classes. Without the
// property, as in the full verify.ps1 run before a commit, every task analyzes all its sources.
val detektFiles = providers.gradleProperty("detektFilesList")
    .flatMap { providers.fileContents(layout.projectDirectory.file(it)).asText }
    .orNull
    ?.lines()
    ?.filter { it.isNotBlank() }
    ?.map { file(it.trim()) }
    ?.toSet()
if (detektFiles != null) {
    subprojects {
        tasks.withType<Detekt>().configureEach {
            // A local copy: the configuration cache cannot store a reference to the build script.
            val analyzed = detektFiles
            // An exclude, not a new source: the plugin adds the sources after this action runs.
            exclude { !it.isDirectory && it.file !in analyzed }
            // The plugin's classpath lacks the module's own classes, which the excluded files now need; a test task
            // gets the tested classes from its libraries. An explicit value replaces the plugin's, so each names both.
            // The classes are friends, as much of the code is internal.
            val compileName = kotlinCompileOf(name)
            if (compileName in tasks.names) {
                val compile = tasks.named<KotlinJvmCompile>(compileName)
                val classes = compile.flatMap { it.destinationDirectory }
                classpath.from(compile.map { it.libraries }, classes)
                friendPaths.from(compile.map { it.friendPaths }, classes)
            }
        }
    }
}

// The Kotlin compile whose classes a detekt task's sources become: detektMain of a JVM module compiles in
// compileKotlin, the rest by the same name, as detektPremiumDebugUnitTest in compilePremiumDebugUnitTestKotlin.
fun kotlinCompileOf(detektTask: String) =
    if (detektTask == "detektMain") "compileKotlin" else detektTask.replaceFirst("detekt", "compile") + "Kotlin"
