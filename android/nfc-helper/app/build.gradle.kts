plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "xyz.dormos.nfc"
    compileSdk = 36
    defaultConfig {
        applicationId = "xyz.dormos.nfc"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
}

