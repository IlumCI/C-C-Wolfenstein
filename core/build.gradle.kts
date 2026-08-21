plugins {
    `java-library`
    application
}

// No toolchain block on purpose: `options.release` below pins the bytecode level, so the
// build works with whatever recent JDK is installed rather than demanding one exact version.
// The Android module compiles these same sources, so the language level must stay
// within what Android's desugaring supports: no records, no sealed types, no var-in-lambda
// tricks beyond Java 8 semantics. Plain classes and enums only.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}

application {
    mainClass.set("com.ccwolf.core.harness.SkirmishHarness")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
