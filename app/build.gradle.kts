plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.livresom"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.livresom"
        minSdk = 29
        targetSdk = 35
        val number = providers.environmentVariable("APP_BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
        versionCode = number
        versionName = "1.0.$number"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("ciRelease") {
            val keystore = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
}
