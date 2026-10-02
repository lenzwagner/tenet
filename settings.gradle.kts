pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Tenet"

include(
    ":app",
    ":core:common",
    ":core:designsystem",
    ":core:database",
    ":core:data",
    ":core:datastore",
    ":feature:today",
    ":feature:sport",
    ":feature:journal",
    ":feature:nutrition",
    ":feature:settings",
    // Emulator-only test tool (Health Connect sample runs), not shipped.
    ":tools:hcseed",
)
