plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.pivisolutions.dictus.whisper"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
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
            }
        }
    }

    // Opt-in x86_64 for debug builds only, to transcribe on an x86_64 emulator:
    //   ./gradlew assembleDebug -Pdictus.emulatorAbi=true
    // Off by default so phone and CI debug builds do not compile whisper.cpp a third
    // time. AGP merges buildType ndk.abiFilters with defaultConfig's; release always
    // keeps the two ARM ABIs it ships.
    buildTypes {
        debug {
            if (providers.gradleProperty("dictus.emulatorAbi").orNull.toBoolean()) {
                ndk {
                    abiFilters += "x86_64"
                }
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
