plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.pivisolutions.dictus.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        // The shared-audio decoder (#204) runs MediaCodec, which only a device or an emulator has:
        // `./gradlew :core:connectedDebugAndroidTest` on dictus_api35. Not run by CI.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One copy of the voice note test audio, a resource for the JVM tests and an asset for the
    // instrumented decoder test.
    sourceSets {
        getByName("test").resources.srcDir("src/testAudio")
        getByName("androidTest").assets.srcDir("src/testAudio")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.timber)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
}
