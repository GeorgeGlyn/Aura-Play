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

rootProject.name = "AuraPlay"
include(":app")
include(":app-tv")
include(":core-network")
include(":core-database")
include(":core-player")
include(":feature-home")
include(":feature-details")
include(":feature-player")
include(":feature-downloads")
include(":feature-remote")
