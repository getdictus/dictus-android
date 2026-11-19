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
    // Not in git (31 MB): tools/fetch-sherpa-onnx-x86_64.sh downloads them. Without them the
    // build still works and Parakeet fails cleanly on the emulator.
    sourceSets {
        getByName("debug") {
            if (providers.gradleProperty("dictus.emulatorAbi").orNull.toBoolean()) {
                jniLibs.srcDir("src/emulator/jniLibs")
                if (!file("src/emulator/jniLibs/x86_64/libsherpa-onnx-jni.so").exists()) {
                    logger.warn(
                        "dictus.emulatorAbi: no sherpa-onnx x86_64 libraries, Parakeet will not load " +
                            "on the emulator. Run tools/fetch-sherpa-onnx-x86_64.sh to add them.",
                    )
                }
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
