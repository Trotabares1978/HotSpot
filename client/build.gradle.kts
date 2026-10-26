plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ar.com.argentas.invitados"
    compileSdk = 35
    defaultConfig {
        applicationId = "ar.com.argentas.invitados"
        minSdk = 26
        targetSdk = 28
        versionCode = 2
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}