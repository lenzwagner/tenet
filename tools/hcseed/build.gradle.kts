// Emulator-only test tool: writes sample runs (exercise + distance + heart
// rate) into Health Connect as a *different* app, so Tenet's import can be
// tested without a watch. Not part of the Tenet app.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tenet.hcseed"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tenet.hcseed"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.health.connect)
}
