plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -PversionCode / -PversionName (derived from the release tag).
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 2
val ciVersionName = (project.findProperty("versionName") as String?)?.removePrefix("v") ?: "1.1"

// Self-signing keystore for this personal app. Generated on first build; commit it so
// every APK (local and CI) carries the same signature and updates install over each other.
val keystoreFile = file("release.keystore")
if (!keystoreFile.exists()) {
    val ext = if (System.getProperty("os.name").lowercase().contains("win")) ".exe" else ""
    val keytool = file(System.getProperty("java.home")).resolve("bin/keytool$ext")
    exec {
        commandLine(
            keytool.absolutePath, "-genkeypair",
            "-keystore", keystoreFile.absolutePath,
            "-alias", "masterbrowse", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000", "-storepass", "masterbrowse", "-keypass", "masterbrowse",
            "-dname", "CN=MasterBrowse",
        )
    }
}

android {
    namespace = "com.kai.masterbrowse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kai.masterbrowse"
        minSdk = 30
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "masterbrowse"
            keyAlias = "masterbrowse"
            keyPassword = "masterbrowse"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Same key as release so sideloaded updates never hit a signature mismatch.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")

    testImplementation("junit:junit:4.13.2")
}
