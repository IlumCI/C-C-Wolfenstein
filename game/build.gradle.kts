plugins {
    `java-library`
}

// The Android module compiles these sources too, so the language level stays inside what
// Android's desugaring supports.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}

dependencies {
    api(project(":core"))
    api(project(":gfx"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    // Rendering tests draw through the AWT backend: no emulator, no Robolectric.
    testImplementation(project(":gfx-java2d"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
