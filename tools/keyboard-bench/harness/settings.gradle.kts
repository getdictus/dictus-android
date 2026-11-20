// A build of its own, outside the app's: the bench drives whatever keyboard is installed and
// never compiles keyboard code, so the app's build and CI stay as they are (#382).
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
    versionCatalogs {
        create("libs") { from(files("../../../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "keyboard-bench-harness"
