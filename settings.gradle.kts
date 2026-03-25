pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dictus-android"
include(":app", ":ime", ":core")
// ":whisper" excluded temporarily -- NDK 25.2.9519653 not installed (Plan 03 will fix)
