import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.tenet.android.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // NVIDIA NIM key from local.properties (not in version control);
        // can be overridden in the app settings.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "NIM_API_KEY", "\"${local.getProperty("nim.apiKey", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)

    implementation(libs.androidx.health.connect)
    implementation(libs.play.services.location)

    // Google sign-in (Credential Manager) + Firebase Auth/Firestore sync
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
