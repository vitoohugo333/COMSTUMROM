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
        // Kadb 2.1.3 depends on Flyfish233/spake2-java, published through JitPack.
        // Keep the repository explicit and scoped to the dependency family.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.Flyfish233")
            }
        }
    }
}

rootProject.name = "CUSTOMROM-ADB"
include(":app")
