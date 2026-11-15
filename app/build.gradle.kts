plugins {
    id("com.android.application")
}

android {
    namespace = "dev.anaekran"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.anaekran"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Kişisel cihaz: release da debug anahtarıyla imzalanır; R8 kullanılmayan Kotlin kodunu atar.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Runtime: only kotlin-stdlib (added by built-in Kotlin). No AndroidX, no third-party libs.
    testImplementation("junit:junit:4.13.2")
}
