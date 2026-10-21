// Standalone host project: NOT part of the root telegram-ui build.
// It consumes tgui exactly like an outside app would, from mavenLocal().
// First run in the repo root:  ./gradlew :tgui:publishToMavenLocal
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
        mavenLocal()
        google()
        mavenCentral()
    }
}
rootProject.name = "tgui-host-app"
include(":app")
