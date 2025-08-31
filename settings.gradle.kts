pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral() // <-- Added as requested
        maven { url = uri("https://jitpack.io") } // <-- Added for Sceneform plugin
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral() // <-- Added as requested
        maven { url = uri("https://jitpack.io") } // <-- Added for Sceneform plugin
    }
}

rootProject.name = "Paxel AR Space Scan"
include(":app")
