// AGP 9 has built-in Kotlin. To use a KGP version higher than the default
// (2.2.10), the Kotlin Gradle plugin must be put on the root buildscript classpath.
// See: https://developer.android.com/build/releases/agp-9-0-0-release-notes
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        // Firebase config (google-services.json → resources); applied only when the file exists.
        classpath(libs.google.services.gradle)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Material 3 Expressive (FloatingToolbar, ButtonGroup, LoadingIndicator, …) is
// still marked experimental – opt in once for every module.
// Material 3 (Expressive) opt-ins only where Compose Material 3 is used;
// the pure data modules would warn about an unresolved marker.
val noMaterial3 = setOf(":core:common", ":core:data", ":core:database", ":core:datastore")
subprojects {
    if (path in noMaterial3) return@subprojects
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions.optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}
