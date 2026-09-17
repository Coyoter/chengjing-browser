pluginManagement {
    buildscript {
        repositories {
            // R8's official release repository also hosts versions not on Google Maven.
            maven {
                url = uri("https://storage.googleapis.com/r8-releases/raw")
                content { includeModule("com.android.tools", "r8") }
            }
            google(); mavenCentral()
        }
        dependencies {
            // Kotlin 2.4 requires R8 >= 9.1.29; keep AGP stable instead of migrating the app.
            classpath("com.android.tools:r8:9.1.29")
        }
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "ChengJingBrowser"
include(":app")
// Independent host: no :app dependency or instrumentation keep rules in the tested release.
include(":release-smoke")
