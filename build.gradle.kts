// Root build file. Per-module configuration lives in core/build.gradle.kts and
// app/build.gradle.kts; the Android plugin is deliberately not declared here so that
// `:core` can be built and tested without the Android SDK present.
tasks.register("cleanAll", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
