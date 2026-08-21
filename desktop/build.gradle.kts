plugins {
    `java-library`
    application
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}

application {
    mainClass.set("com.ccwolf.desktop.DesktopMain")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":game"))
    implementation(project(":gfx-java2d"))
}
