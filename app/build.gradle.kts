plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.oubliettes"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.all3f0r1.oubliettes"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.1"
    }
    buildFeatures { compose = true }
    // F-Droid rejects the encrypted dependency list AGP embeds for Google Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Release is signed only when -PkeystorePassword (or ORG_GRADLE_PROJECT_keystorePassword) is set.
    val keystorePassword = providers.gradleProperty("keystorePassword").orNull?.takeIf { it.isNotEmpty() }
    if (keystorePassword != null) {
        signingConfigs.create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = keystorePassword
            keyAlias = "oubliettes"
            keyPassword = keystorePassword
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
