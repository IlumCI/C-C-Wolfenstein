plugins {
    `java-library`
}

// Plain Java, no platform of any kind. The Android module compiles these sources too, so the
// language level has to stay inside what Android's desugaring supports.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}
