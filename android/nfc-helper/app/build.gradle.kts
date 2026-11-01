plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = System.getenv("DORMOS_RELEASE_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("DORMOS_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("DORMOS_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("DORMOS_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "xyz.dormos.nfc"
    compileSdk = 36
    buildFeatures { compose = true }
    defaultConfig {
        applicationId = "xyz.dormos.nfc"
        minSdk = 29
        targetSdk = 36
        versionCode = 28
        versionName = "1.16.0-inline-ndef-sheet"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storeType = "JKS"
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
