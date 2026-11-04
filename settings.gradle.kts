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

rootProject.name = "CCRecorderAndroid"

include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:media")
include(":feature:recorder")
include(":feature:recordings")
include(":feature:conversation")
include(":feature:settings")
