plugins {
    id("com.android.application") version "8.7.2"
}

android {
    namespace = "com.ccwolf.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ccwolf.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":gfx"))
    implementation(project(":audio"))
    implementation(project(":game"))

    // The rendering and art tests live in :game now and run on a plain JVM through the AWT
    // backend - faster than Robolectric was, and no SDK anywhere near them. What is left here
    // is the Android shell, which needs a device to mean anything.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
