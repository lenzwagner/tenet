plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

// Google-Anmeldung + Sync: needs app/google-services.json from the Firebase
// console. Without it the app builds and runs, sign-in shows "not set up".
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "app.tenet.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tenet.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 75
        versionName = "1.0.0.18"
    }

    buildTypes {
        release {
            // R8 + resource shrinking: Compose runs noticeably smoother when
            // optimized (inlining, no debug/live-literal overhead).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.coil.network.okhttp)
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:today"))
    implementation(project(":feature:sport"))
    implementation(project(":feature:journal"))
    implementation(project(":feature:nutrition"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.haze)
    implementation(libs.haze.blur)

    // Installs the Baseline Profile (src/release/generated/baselineProfiles) on sideloaded APKs too.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.biometric)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
