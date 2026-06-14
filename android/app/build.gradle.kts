import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.keyboardai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.keyboardai.app"
        // LEAP SDK requires API 31+; the on-device model needs a modern phone anyway.
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.0"

        // The LEAP SDK (llama.cpp/ggml) ships only arm64-v8a native libs.
        ndk { abiFilters += "arm64-v8a" }
    }

    // GGUF weights are bundled under assets/models/. Keep them uncompressed so the
    // APK isn't bloated by zip-deflating already-quantized data and so extraction
    // to private storage is a fast straight copy.
    androidResources {
        noCompress += "gguf"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // On-device LLM/VLM runtime (LFM2-VL models). llama.cpp-backed, arm64 only.
    implementation("ai.liquid.leap:leap-sdk:0.10.9")
}

