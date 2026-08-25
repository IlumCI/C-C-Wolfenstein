plugins {
    `java-library`
}

// Kept out of :audio on purpose, exactly as java.awt is kept out of :gfx: the Android module
// compiles :audio, and javax.sound.sampled does not exist there.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}

dependencies {
    api(project(":audio"))
}
