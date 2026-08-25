pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cc-wolfenstein"

include(":core")
include(":gfx")
include(":gfx-java2d")
include(":audio")
include(":audio-javasound")
include(":game")
include(":desktop")

// The `:app` module needs the Android SDK. Including it unconditionally would make even
// `gradle :core:test` fail on a machine without the SDK (Gradle configures every project),
// so the Android module only joins the build when an SDK is actually reachable.
val sdkFromProperties: String? = file("local.properties").takeIf { it.exists() }?.let { f ->
    java.util.Properties().apply { f.inputStream().use { load(it) } }.getProperty("sdk.dir")
}
val sdkDir: String? = sdkFromProperties
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")

if (sdkDir != null && file(sdkDir).isDirectory) {
    include(":app")
} else {
    logger.lifecycle("[cc-wolfenstein] No Android SDK found - building :core only. " +
        "Set ANDROID_HOME or local.properties (sdk.dir=...) to build the Android app.")
}
