plugins {
    `java-library`
}

// Kept out of :gfx on purpose. The Android module compiles :gfx, and java.awt does not exist
// there — a backend and its interface have to be separable or every platform pays for all of
// them.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}

dependencies {
    api(project(":gfx"))
}
