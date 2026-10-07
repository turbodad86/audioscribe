plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.audioscribe.companion"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.audioscribe.companion"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // signed with the debug key so the APK installs straight away; use your own key to publish
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("webassets"))
}

// the app is the same web page the PC serves (../index.html), bundled so it opens without the PC
val copyWeb by tasks.registering(Copy::class) {
    from(rootDir.parentFile) { include("*.html", "*.png", "*.svg", "*.webmanifest") }
    into(layout.buildDirectory.dir("webassets"))
}
tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media:media:1.7.0")
}
