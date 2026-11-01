plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Stable release signing is opt-in and supplied only through local environment / CI secrets.
// Never commit the keystore or passwords. Debug builds remain independent of these values.
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
    defaultConfig {
        applicationId = "xyz.dormos.nfc"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0-preview"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

