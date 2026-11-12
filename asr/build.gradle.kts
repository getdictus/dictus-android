plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.pivisolutions.dictus.asr"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    // sherpa-onnx's x86_64 libraries, for debug builds that opt in with
    // -Pdictus.emulatorAbi=true (same rule as :whisper and :trie), so Parakeet runs on an
    // x86_64 emulator. Phone, CI and release builds ship arm64-v8a and armeabi-v7a only.
    sourceSets {
        getByName("debug") {
            if (providers.gradleProperty("dictus.emulatorAbi").orNull.toBoolean()) {
                jniLibs.srcDir("src/emulator/jniLibs")
            }
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

dependencies {
    implementation(project(":core"))
    implementation(libs.timber)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
