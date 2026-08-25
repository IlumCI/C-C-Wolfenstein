plugins {
    `java-library`
}

// Plain Java, no platform of any kind — the audio twin of :gfx. The Android module compiles
// these sources too, so the language level has to stay inside what desugaring supports.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}
