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

rootProject.name = "YamoneGames"
include(":app")
include(":games:arcadecore")
include(":games:sudoku")
include(":games:icejump")
include(":games:fishmunch")
include(":games:snowrush")
include(":games:winterride")
