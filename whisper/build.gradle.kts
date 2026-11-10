plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Optional host-emulator validation. Shipped Android ABIs remain ARM unless explicitly requested.
val dictusTestAbi = providers.gradleProperty("dictusTestAbi").orNull
require(dictusTestAbi == null || dictusTestAbi == "x86_64") { "dictusTestAbi supports x86_64 only" }

android {
    namespace = "dev.pivisolutions.dictus.whisper"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters += if (dictusTestAbi != null) listOf(dictusTestAbi) else listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Force Release build for native libs even in debug APK.
    // whisper.cpp without -O3 is ~70x slower (34s vs <1s for tiny model).
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments("-DCMAKE_BUILD_TYPE=Release")
                if (dictusTestAbi != null) arguments("-DGGML_NATIVE=OFF")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/whisper/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    ndkVersion = libs.versions.ndk.get()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.timber)
    implementation(libs.coroutines.android)
}
