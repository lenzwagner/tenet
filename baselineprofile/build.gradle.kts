// Generates app/src/release/generated/baselineProfiles: the code paths of
// startup and the main tabs get compiled ahead of time on install, so the
// app starts and scrolls faster from the first launch (no JIT warm-up).
// Run with one device connected (or ANDROID_SERIAL set):
//   ./gradlew :app:generateReleaseBaselineProfile
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.tenet.android.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
