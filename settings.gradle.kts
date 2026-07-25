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
        // Required for: com.github.gcacace:signature-pad
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BeaverGuardian"
include(":app")
