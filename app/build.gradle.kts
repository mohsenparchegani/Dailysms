plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.yalda.dailysms"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.yalda.dailysms"
        minSdk = 24
        targetSdk = 34
        versionCode = 12
        versionName = "1.11"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
